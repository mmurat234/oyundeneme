package com.mihrimah.kedi;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/** Küçük ses efektleri: hepsi kodla üretilir, dosya gerekmez. */
public class Sound {

    private static final int RATE = 22050;

    private final short[] meow;
    private final short[] purr;
    private final short[] pop;
    private final short[] whoosh;
    private final short[] fanfare;
    private final short[] sad;
    private final short[] ding;

    public boolean enabled = true;

    public Sound() {
        meow = makeMeow();
        purr = makePurr();
        pop = makeTone(new float[]{880, 1320}, 0.07f, 0.5f);
        whoosh = makeWhoosh();
        fanfare = makeTone(new float[]{523, 659, 784, 1047}, 0.13f, 0.45f);
        sad = makeTone(new float[]{392, 330, 262}, 0.22f, 0.4f);
        ding = makeTone(new float[]{1175, 1568}, 0.08f, 0.35f);
    }

    public void meow() { play(meow); }
    public void purr() { play(purr); }
    public void pop() { play(pop); }
    public void whoosh() { play(whoosh); }
    public void fanfare() { play(fanfare); }
    public void sad() { play(sad); }
    public void ding() { play(ding); }

    private void play(final short[] data) {
        if (!enabled) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                AudioTrack t = null;
                try {
                    t = new AudioTrack(AudioManager.STREAM_MUSIC, RATE,
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                            data.length * 2, AudioTrack.MODE_STATIC);
                    t.write(data, 0, data.length);
                    t.play();
                    Thread.sleep(data.length * 1000L / RATE + 60);
                } catch (Throwable ignored) {
                } finally {
                    if (t != null) {
                        try { t.stop(); } catch (Throwable ignored) { }
                        t.release();
                    }
                }
            }
        }).start();
    }

    private static short[] makeMeow() {
        int n = (int) (RATE * 0.55f);
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            // "mi-yav": yükselip alçalan perde + hafif titreşim
            double f = 520 + 520 * Math.sin(Math.PI * Math.min(1, t * 1.3)) - 150 * t
                    + 18 * Math.sin(i * 2 * Math.PI * 7 / RATE);
            phase += 2 * Math.PI * f / RATE;
            double s = Math.sin(phase) * 0.6 + Math.sin(phase * 2) * 0.25 + Math.sin(phase * 3) * 0.1;
            double env = Math.min(1, t * 12) * Math.pow(1 - t, 1.2);
            out[i] = (short) (s * env * 9000);
        }
        return out;
    }

    private static short[] makePurr() {
        int n = (int) (RATE * 0.9f);
        short[] out = new short[n];
        java.util.Random r = new java.util.Random(7);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double noise = r.nextDouble() * 2 - 1;
            lp += (noise - lp) * 0.08;
            double am = 0.5 + 0.5 * Math.sin(i * 2 * Math.PI * 24 / RATE);
            double env = Math.min(1, t * 8) * Math.min(1, (1 - t) * 6);
            out[i] = (short) (lp * am * env * 26000);
        }
        return out;
    }

    private static short[] makeWhoosh() {
        int n = (int) (RATE * 0.35f);
        short[] out = new short[n];
        java.util.Random r = new java.util.Random(3);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double k = 0.02 + 0.25 * t;
            lp += ((r.nextDouble() * 2 - 1) - lp) * k;
            double env = Math.sin(Math.PI * t);
            out[i] = (short) (lp * env * 14000);
        }
        return out;
    }

    private static short[] makeTone(float[] notes, float noteLen, float vol) {
        int per = (int) (RATE * noteLen);
        short[] out = new short[per * notes.length];
        for (int k = 0; k < notes.length; k++) {
            double phase = 0;
            for (int i = 0; i < per; i++) {
                float t = i / (float) per;
                phase += 2 * Math.PI * notes[k] / RATE;
                double s = Math.sin(phase) + 0.3 * Math.sin(phase * 2);
                double env = Math.min(1, t * 30) * (1 - t);
                out[k * per + i] = (short) (s * env * vol * 16000);
            }
        }
        return out;
    }
}
