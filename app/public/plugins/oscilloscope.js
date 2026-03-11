export default {
  type:    "visual",
  name:    "oscilloscope",
  version: "1.0.0",

  init(host) {
    this._analyser = host.analyser;
    this._running  = false;
    this._canvas   = null;
    this._raf      = null;
  },

  mount(container) {
    this._canvas = document.createElement("canvas");
    this._canvas.width  = container.clientWidth || 600;
    this._canvas.height = 80;
    container.appendChild(this._canvas);
    this._running = true;
    this._draw();
  },

  unmount() {
    this._running = false;
    if (this._raf) cancelAnimationFrame(this._raf);
    if (this._canvas) this._canvas.remove();
    this._canvas = null;
  },

  destroy() {
    this.unmount();
    this._analyser = null;
  },

  _draw() {
    if (!this._running) return;
    const ctx = this._canvas.getContext("2d");
    const buf = new Uint8Array(this._analyser.fftSize);
    this._analyser.getByteTimeDomainData(buf);
    const W = this._canvas.width, H = this._canvas.height;
    ctx.clearRect(0, 0, W, H);
    ctx.strokeStyle = "#7fffff";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    for (let i = 0; i < buf.length; i++) {
      const x = (i / buf.length) * W;
      const y = (buf[i] / 128.0) * (H / 2);
      i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    }
    ctx.stroke();
    this._raf = requestAnimationFrame(() => this._draw());
  }
};
