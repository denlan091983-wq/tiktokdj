const MAX_MOOV_BYTES = 4 * 1024 * 1024;
const MAX_TOP_LEVEL_BOXES = 128;
const MAX_CHILD_BOXES = 4096;
const RANGE_TIMEOUT_MS = 30_000;

export type M4aInfo = {
  durationSeconds: number;
  sizeBytes: number;
};

type BoxHeader = {
  type: string;
  start: number;
  size: number;
  headerSize: number;
  payloadStart: number;
  end: number;
};

function invalid(message: string): never {
  throw new Error(`Ungültige M4A-Datei: ${message}`);
}

function readBoxHeader(buffer: Buffer, localOffset: number, absoluteOffset: number, parentEnd: number): BoxHeader {
  if (localOffset < 0 || localOffset + 8 > buffer.length) invalid("MP4-Boxheader ist abgeschnitten.");
  const size32 = buffer.readUInt32BE(localOffset);
  const type = buffer.toString("ascii", localOffset + 4, localOffset + 8);
  let headerSize = 8;
  let size: number;

  if (size32 === 1) {
    if (localOffset + 16 > buffer.length) invalid("erweiterter MP4-Boxheader ist abgeschnitten.");
    const extended = buffer.readBigUInt64BE(localOffset + 8);
    if (extended > BigInt(Number.MAX_SAFE_INTEGER)) invalid("MP4-Box ist zu groß.");
    size = Number(extended);
    headerSize = 16;
  } else if (size32 === 0) {
    size = parentEnd - absoluteOffset;
  } else {
    size = size32;
  }

  if (size < headerSize || absoluteOffset + size > parentEnd) invalid(`ungültige Größe in Box ${type}.`);
  return {
    type,
    start: absoluteOffset,
    size,
    headerSize,
    payloadStart: absoluteOffset + headerSize,
    end: absoluteOffset + size,
  };
}

function readChildBoxes(buffer: Buffer, parent: BoxHeader): BoxHeader[] {
  const boxes: BoxHeader[] = [];
  let offset = parent.payloadStart;
  while (offset < parent.end) {
    if (boxes.length >= MAX_CHILD_BOXES) invalid("zu viele verschachtelte MP4-Boxen.");
    const box = readBoxHeader(buffer, offset, offset, parent.end);
    boxes.push(box);
    offset = box.end;
  }
  if (offset !== parent.end) invalid("MP4-Boxen sind nicht korrekt ausgerichtet.");
  return boxes;
}

function fullBoxDuration(buffer: Buffer, box: BoxHeader): number {
  if (box.payloadStart + 4 > box.end) invalid(`${box.type} ist unvollständig.`);
  const version = buffer[box.payloadStart];
  let timescaleOffset: number;
  let durationOffset: number;
  let durationBytes: number;

  if (version === 0) {
    timescaleOffset = box.payloadStart + 12;
    durationOffset = box.payloadStart + 16;
    durationBytes = 4;
  } else if (version === 1) {
    timescaleOffset = box.payloadStart + 20;
    durationOffset = box.payloadStart + 24;
    durationBytes = 8;
  } else {
    invalid(`nicht unterstützte ${box.type}-Version ${version}.`);
  }

  if (durationOffset + durationBytes > box.end) invalid(`${box.type}-Dauerfeld ist unvollständig.`);
  const timescale = buffer.readUInt32BE(timescaleOffset);
  if (timescale === 0) invalid(`${box.type}-Zeitskala ist ungültig.`);
  const ticks = durationBytes === 8
    ? buffer.readBigUInt64BE(durationOffset)
    : BigInt(buffer.readUInt32BE(durationOffset));
  const seconds = Number(ticks) / timescale;
  if (!Number.isFinite(seconds) || seconds < 0) invalid(`${box.type}-Dauer ist ungültig.`);
  return seconds;
}

function audioSampleEntryIsAac(buffer: Buffer, mdia: BoxHeader): boolean {
  const mdiaChildren = readChildBoxes(buffer, mdia);
  const minf = mdiaChildren.find(box => box.type === "minf");
  if (!minf) return false;
  const stbl = readChildBoxes(buffer, minf).find(box => box.type === "stbl");
  if (!stbl) return false;
  const stsd = readChildBoxes(buffer, stbl).find(box => box.type === "stsd");
  if (!stsd || stsd.payloadStart + 8 > stsd.end) return false;

  const entryCount = buffer.readUInt32BE(stsd.payloadStart + 4);
  if (entryCount < 1 || entryCount > 64) return false;
  const entriesParent: BoxHeader = {
    type: "stsd-entries",
    start: stsd.payloadStart + 8,
    size: stsd.end - (stsd.payloadStart + 8),
    headerSize: 0,
    payloadStart: stsd.payloadStart + 8,
    end: stsd.end,
  };
  const entries = readChildBoxes(buffer, entriesParent);
  return entries.length === entryCount && entries.some(entry => entry.type === "mp4a");
}

/** Parses the movie presentation duration and confirms that its playable audio track is AAC. */
export function parseM4aDurationFromMoov(moov: Buffer): number {
  const root = readBoxHeader(moov, 0, 0, moov.length);
  if (root.type !== "moov" || root.end !== moov.length) invalid("moov-Box fehlt oder ist abgeschnitten.");

  const topChildren = readChildBoxes(moov, root);
  const movieHeader = topChildren.find(box => box.type === "mvhd");
  const movieDuration = movieHeader ? fullBoxDuration(moov, movieHeader) : 0;
  let audioTrackCount = 0;
  let longestAudioTrackSeconds = 0;

  for (const track of topChildren.filter(box => box.type === "trak")) {
    const trackChildren = readChildBoxes(moov, track);
    const mdia = trackChildren.find(box => box.type === "mdia");
    if (!mdia) continue;
    const mediaChildren = readChildBoxes(moov, mdia);
    const handler = mediaChildren.find(box => box.type === "hdlr");
    if (!handler || handler.payloadStart + 12 > handler.end) continue;
    const handlerType = moov.toString("ascii", handler.payloadStart + 8, handler.payloadStart + 12);
    if (handlerType === "vide") invalid("Videospuren sind in Radioaufnahmen nicht erlaubt.");
    if (handlerType !== "soun") continue;

    const mediaHeader = mediaChildren.find(box => box.type === "mdhd");
    if (!mediaHeader) invalid("Audiotrack-Dauer fehlt.");
    if (!audioSampleEntryIsAac(moov, mdia)) invalid("Audiospur ist kein unterstütztes AAC-Audio.");
    audioTrackCount += 1;
    longestAudioTrackSeconds = Math.max(longestAudioTrackSeconds, fullBoxDuration(moov, mediaHeader));
  }

  if (audioTrackCount === 0) invalid("keine AAC-Audiospur gefunden.");
  const durationSeconds = movieDuration > 0 ? movieDuration : longestAudioTrackSeconds;
  if (!Number.isFinite(durationSeconds) || durationSeconds <= 0) invalid("Audiodauer ist leer oder ungültig.");
  return durationSeconds;
}

async function fetchByteRange(url: string, start: number, end: number): Promise<{ bytes: Buffer; totalBytes: number }> {
  const response = await fetch(url, {
    headers: { Range: `bytes=${start}-${end}` },
    signal: AbortSignal.timeout(RANGE_TIMEOUT_MS),
  });
  if (response.status !== 206) {
    await response.body?.cancel().catch(() => undefined);
    throw new Error("Audiospeicher unterstützt keine bestätigten Byte-Range-Lesevorgänge.");
  }

  const match = /^bytes\s+(\d+)-(\d+)\/(\d+)$/i.exec(response.headers.get("content-range") ?? "");
  if (!match) throw new Error("Audiospeicher lieferte keinen gültigen Content-Range.");
  const returnedStart = Number(match[1]);
  const returnedEnd = Number(match[2]);
  const totalBytes = Number(match[3]);
  if (
    returnedStart !== start || returnedEnd < start || returnedEnd > end ||
    !Number.isSafeInteger(totalBytes) || totalBytes <= returnedEnd
  ) {
    throw new Error("Audiospeicher lieferte inkonsistente Bereichsmetadaten.");
  }

  const bytes = Buffer.from(await response.arrayBuffer());
  if (bytes.length !== returnedEnd - returnedStart + 1) throw new Error("Audiospeicher lieferte einen unvollständigen Bereich.");
  return { bytes, totalBytes };
}

/** Reads only MP4 headers and the bounded moov metadata box from a short-lived signed URL. */
export async function readM4aInfoFromUrl(url: string): Promise<M4aInfo> {
  const first = await fetchByteRange(url, 0, 15);
  const totalBytes = first.totalBytes;
  const ftyp = readBoxHeader(first.bytes, 0, 0, totalBytes);
  if (ftyp.type !== "ftyp" || ftyp.size < 16) invalid("M4A-Dateikennung fehlt.");

  let offset = ftyp.end;
  for (let count = 0; count < MAX_TOP_LEVEL_BOXES && offset + 8 <= totalBytes; count += 1) {
    const headerEnd = Math.min(offset + 15, totalBytes - 1);
    const range = await fetchByteRange(url, offset, headerEnd);
    if (range.totalBytes !== totalBytes) throw new Error("Objektgröße änderte sich während der M4A-Prüfung.");
    const box = readBoxHeader(range.bytes, 0, offset, totalBytes);
    if (box.type === "moov") {
      if (box.size > MAX_MOOV_BYTES) invalid("M4A-Metadaten überschreiten das Sicherheitslimit.");
      const completeMoov = await fetchByteRange(url, box.start, box.end - 1);
      if (completeMoov.totalBytes !== totalBytes || completeMoov.bytes.length !== box.size) {
        throw new Error("M4A-Metadaten konnten nicht vollständig gelesen werden.");
      }
      return { durationSeconds: parseM4aDurationFromMoov(completeMoov.bytes), sizeBytes: totalBytes };
    }
    offset = box.end;
  }

  invalid("moov-Metadatenbox wurde nicht gefunden.");
}
