// Polyfills for APIs missing from AudioWorkletGlobalScope.
// This module is imported FIRST in worklet.js so it is evaluated before the
// wasm-pack glue (repulse_audio.js) which uses TextDecoder and TextEncoder
// at module top-level.

if (typeof TextDecoder === 'undefined') {
  globalThis.TextDecoder = class TextDecoder {
    constructor(encoding, options) {
      this.encoding = encoding || 'utf-8';
      this.fatal = !!(options && options.fatal);
    }
    decode(buffer) {
      if (!buffer) return '';
      const bytes = (buffer instanceof ArrayBuffer)
        ? new Uint8Array(buffer)
        : new Uint8Array(buffer.buffer, buffer.byteOffset, buffer.byteLength);
      if (bytes.length === 0) return '';
      let str = '', i = 0;
      while (i < bytes.length) {
        const b = bytes[i];
        if (b < 0x80) {
          str += String.fromCharCode(b); i++;
        } else if ((b & 0xe0) === 0xc0) {
          str += String.fromCharCode(((b & 0x1f) << 6) | (bytes[i + 1] & 0x3f)); i += 2;
        } else if ((b & 0xf0) === 0xe0) {
          str += String.fromCharCode(
            ((b & 0x0f) << 12) | ((bytes[i + 1] & 0x3f) << 6) | (bytes[i + 2] & 0x3f)); i += 3;
        } else {
          const cp = ((b & 0x07) << 18) | ((bytes[i + 1] & 0x3f) << 12)
                   | ((bytes[i + 2] & 0x3f) << 6) | (bytes[i + 3] & 0x3f);
          const o = cp - 0x10000;
          str += String.fromCharCode(0xd800 + (o >> 10), 0xdc00 + (o & 0x3ff)); i += 4;
        }
      }
      return str;
    }
  };
}

if (typeof TextEncoder === 'undefined') {
  globalThis.TextEncoder = class TextEncoder {
    constructor() { this.encoding = 'utf-8'; }
    encode(str) {
      if (!str) return new Uint8Array(0);
      const bytes = [];
      for (let i = 0; i < str.length; i++) {
        const c = str.charCodeAt(i);
        if (c < 0x80)       bytes.push(c);
        else if (c < 0x800) bytes.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
        else                bytes.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
      }
      return new Uint8Array(bytes);
    }
    encodeInto(str, dest) {
      const encoded = this.encode(str);
      dest.set(encoded);
      return { read: str.length, written: encoded.length };
    }
  };
}
