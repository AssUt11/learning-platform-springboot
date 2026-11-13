import { deflateSync } from 'node:zlib';

// Tiny deterministic initials for demo identities, without copying a real person's photo.
export function demoAvatar(letter, color) {
  const glyphs = { N: ['10001','11001','10101','10011','10001','10001','10001'], L: ['10000','10000','10000','10000','10000','10000','11111'], Z: ['11111','00001','00010','00100','01000','10000','11111'], C: ['01111','10000','10000','10000','10000','10000','01111'], T: ['11111','00100','00100','00100','00100','00100','00100'] };
  glyphs.A = ['01110','10001','10001','11111','10001','10001','10001'];
  const size = 256, stride = size * 4 + 1, raw = Buffer.alloc(size * stride);
  const rgb = color.match(/../g).map(value => parseInt(value, 16));
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const offset = y * stride + 1 + x * 4;
    raw[offset] = rgb[0]; raw[offset + 1] = rgb[1]; raw[offset + 2] = rgb[2]; raw[offset + 3] = 255;
  }
  for (const [gy, row] of glyphs[letter].entries()) for (const [gx, bit] of [...row].entries()) if (bit === '1')
    for (let y = 0; y < 18; y++) for (let x = 0; x < 18; x++) {
      const offset = (65 + gy * 18 + y) * stride + 1 + (83 + gx * 18 + x) * 4;
      raw[offset] = raw[offset + 1] = raw[offset + 2] = 255;
    }
  function chunk(type, data) {
    const name = Buffer.from(type), body = Buffer.concat([name, data]);
    let crc = 0xffffffff;
    for (const value of body) { crc ^= value; for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0); }
    const length = Buffer.alloc(4), checksum = Buffer.alloc(4);
    length.writeUInt32BE(data.length); checksum.writeUInt32BE((crc ^ 0xffffffff) >>> 0);
    return Buffer.concat([length, body, checksum]);
  }
  const header = Buffer.alloc(13); header.writeUInt32BE(size, 0); header.writeUInt32BE(size, 4); header[8] = 8; header[9] = 6;
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', header), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
