// p5-waveform — waveform visualiser built with p5.js.
//
// Reads the time-domain waveform from the master AnalyserNode each frame
// and draws it as a continuous line using p5's HSB colour mode.
//
// Load from the Lisp REPL:
//   (load-plugin "/plugins/p5-waveform.js")

import { makeP5Plugin } from "/plugins/p5-base.js";

export default makeP5Plugin("p5-waveform", "1.0.0", (p, analyser) => {
  const N        = 1024;
  const waveform = new Uint8Array(N);

  p.setup = () => {
    p.createCanvas(p.windowWidth, 120);
    p.colorMode(p.HSB, 360, 100, 100, 100);
    p.noFill();
  };

  p.draw = () => {
    analyser.getByteTimeDomainData(waveform);
    p.background(0, 0, 10, 80);
    p.stroke(180, 80, 100);
    p.strokeWeight(1.5);
    p.beginShape();
    for (let i = 0; i < N; i++) {
      const x = p.map(i, 0, N, 0, p.width);
      const y = p.map(waveform[i], 0, 255, p.height, 0);
      p.vertex(x, y);
    }
    p.endShape();
  };

  p.windowResized = () => {
    p.resizeCanvas(p.windowWidth, 120);
  };
});
