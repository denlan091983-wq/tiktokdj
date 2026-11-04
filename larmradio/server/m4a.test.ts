import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
import { describe, expect, it } from "vitest";
import { readM4aInfoFromUrl } from "./m4a";

function atom(type: string, payload: Buffer): Buffer {
  const header = Buffer.alloc(8);
  header.writeUInt32BE(payload.length + 8, 0);
  header.write(type, 4, 4, "ascii");
  return Buffer.concat([header, payload]);
}

function fullDurationBox(type: "mvhd" | "mdhd", seconds: number): Buffer {
  const payload = Buffer.alloc(20);
  payload.writeUInt32BE(48_000, 12);
  payload.writeUInt32BE(Math.round(seconds * 48_000), 16);
  return atom(type, payload);
}

function makeM4a(durationSeconds: number, handlerType = "soun"): Buffer {
  const ftyp = atom("ftyp", Buffer.from("M4A \0\0\0\0M4A isom", "binary"));
  const mdat = atom("mdat", Buffer.from([0x21, 0x10, 0x01, 0x00]));

  const hdlrPayload = Buffer.alloc(12);
  hdlrPayload.write(handlerType, 8, 4, "ascii");
  const sampleEntry = atom("mp4a", Buffer.alloc(28));
  const stsdPrefix = Buffer.alloc(8);
  stsdPrefix.writeUInt32BE(1, 4);
  const stsd = atom("stsd", Buffer.concat([stsdPrefix, sampleEntry]));
  const stbl = atom("stbl", stsd);
  const minf = atom("minf", stbl);
  const mdia = atom("mdia", Buffer.concat([
    atom("hdlr", hdlrPayload),
    fullDurationBox("mdhd", durationSeconds),
    minf,
  ]));
  const trak = atom("trak", mdia);
  const moov = atom("moov", Buffer.concat([fullDurationBox("mvhd", durationSeconds), trak]));
  return Buffer.concat([ftyp, mdat, moov]);
}

async function withRangeServer(data: Buffer, run: (url: string) => Promise<void>) {
  const server = createServer((request, response) => {
    const range = /^bytes=(\d+)-(\d+)$/i.exec(request.headers.range ?? "");
    if (!range) {
      response.writeHead(400).end();
      return;
    }
    const start = Number(range[1]);
    const end = Math.min(Number(range[2]), data.length - 1);
    if (start < 0 || end < start || start >= data.length) {
      response.writeHead(416, { "Content-Range": `bytes */${data.length}` }).end();
      return;
    }
    const bytes = data.subarray(start, end + 1);
    response.writeHead(206, {
      "Accept-Ranges": "bytes",
      "Content-Range": `bytes ${start}-${end}/${data.length}`,
      "Content-Length": String(bytes.length),
    }).end(bytes);
  });

  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
  const address = server.address() as AddressInfo;
  try {
    await run(`http://127.0.0.1:${address.port}/fixture.m4a`);
  } finally {
    await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  }
}

describe("M4A metadata verification", () => {
  it("reads the actual duration and object size with bounded byte-range requests", async () => {
    const file = makeM4a(900);
    await withRangeServer(file, async url => {
      await expect(readM4aInfoFromUrl(url)).resolves.toEqual({ durationSeconds: 900, sizeBytes: file.length });
    });
  });

  it("returns the actual over-limit duration instead of trusting the browser-declared value", async () => {
    const file = makeM4a(3600.1);
    await withRangeServer(file, async url => {
      const info = await readM4aInfoFromUrl(url);
      expect(info.durationSeconds).toBeGreaterThan(3600);
    });
  });

  it("rejects M4A containers whose only track is not an AAC audio track", async () => {
    const file = makeM4a(60, "vide");
    await withRangeServer(file, async url => {
      await expect(readM4aInfoFromUrl(url)).rejects.toThrow(/Videospuren|keine AAC-Audiospur/);
    });
  });
});
