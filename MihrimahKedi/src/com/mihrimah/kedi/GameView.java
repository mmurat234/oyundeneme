package com.mihrimah.kedi;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Random;

/**
 * "Pamuk'u Yakala!" — Mihrimah ve babası Murat, bahçede kaçan yavru kedi Pamuk'u yakalamaya çalışır.
 * Oyuncu parmağıyla Mihrimah'ı yönlendirir; Murat Baba kediyi öbür taraftan sıkıştırır.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    // ---- Durumlar ----
    private static final int TITLE = 0, PLAYING = 1, LEVEL_DONE = 2, GAME_OVER = 3;
    private static final int CAT_RUN = 0, CAT_EAT = 1, CAT_CAUGHT = 2, CAT_HIDE = 3, CAT_JUMP = 4;
    private static final int OB_BUSH = 0, OB_TREE = 1, OB_POT = 2, OB_BOX = 3;
    private static final int SPK_MIH = 0, SPK_BABA = 1, SPK_CAT = 2;

    private static final String[] THEME_NAMES = {"Arka Bahçe", "Gün Batımı Parkı", "Gece Bahçesi"};

    private final Object lock = new Object();
    private final SurfaceHolder holder;
    private Thread thread;
    private volatile boolean running;
    private volatile boolean surfaceReady;

    private final Random rnd = new Random();
    private final Sound snd = new Sound();
    private final SharedPreferences prefs;

    // ---- Ekran ----
    private int W, H;
    private float U, hudH;
    private float fLeft, fRight, fTop, fBottom;
    private Bitmap bg;
    private int bgTheme = -1;

    // ---- Genel oyun durumu ----
    private int state = TITLE;
    private float stateTime;
    private float globalTime;
    private boolean paused;
    private int level = 1, score, best, catches, target, fishCount;
    private float timeLeft, levelBonus;
    private boolean everTouched;

    // ---- Mihrimah ----
    private float mx, my, mPhase, mBoost;
    private int mFace = 1;
    private boolean mMoving;

    // ---- Murat Baba ----
    private float bx, by, bPhase, dashT, dashCd;
    private int bFace = -1;
    private boolean bMoving;

    // ---- Pamuk (kedi) ----
    private float cx, cy, cvx, cvy, stamina = 1, catTimer, tailPhase, wanderAng, jumpCd, jumpVx, jumpVy;
    private float legPhase, meowCd, catchFromX, catchFromY;
    private int cFace = 1, catState = CAT_RUN, catcher, hideBox = -1;

    // ---- Dokunma ----
    private boolean touching;
    private int movePointer = -1;
    private float tx, ty;

    // ---- Nesneler ----
    private final ArrayList<float[]> obstacles = new ArrayList<float[]>();
    private float fishX, fishY, fishLife;
    private boolean fishOn;
    private float puX, puY, puLife, puSpawn;
    private int puType; // 0 yıldız (hız), 1 balık
    private boolean puOn;
    private final float[] bfx = new float[4], bfy = new float[4], bfa = new float[4], bfp = new float[4];
    private final int[] bfColor = {0xFFFFB3E6, 0xFFFFE066, 0xFF9AD0FF, 0xFFC7A6FF};

    private final ArrayList<P> parts = new ArrayList<P>();
    private final ArrayList<FT> texts = new ArrayList<FT>();
    private final String[] bubble = new String[3];
    private final float[] bubbleT = new float[3];
    private float chatterCd = 4;

    // ---- Butonlar ----
    private float fishBx, fishBy, dashBx, dashBy, btnR, pauseBx, pauseBy;
    private final RectF playBtn = new RectF(), soundBtn = new RectF(), againBtn = new RectF(), menuBtn = new RectF();

    // ---- Çizim araçları ----
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private final int[] order = new int[64];
    private final float[] orderY = new float[64];

    private static class P {
        float x, y, vx, vy, life, max, size;
        int type, color;
    }

    private static class FT {
        String s;
        float x, y, life;
        int color;
    }

    public GameView(Context ctx) {
        super(ctx);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        prefs = ctx.getSharedPreferences("pamuk", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        snd.enabled = prefs.getBoolean("sound", true);
        tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        tp.setTextAlign(Paint.Align.CENTER);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setStrokeJoin(Paint.Join.ROUND);
        sp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        sp.setTextAlign(Paint.Align.CENTER);
    }

    // =====================================================================
    //  Yaşam döngüsü
    // =====================================================================

    public void resume() {
        running = true;
        thread = new Thread(this, "oyun");
        thread.start();
    }

    public void pause() {
        running = false;
        synchronized (lock) {
            if (state == PLAYING) paused = true;
            touching = false;
        }
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { }
        }
    }

    /** Geri tuşu: oyundaysa menüye dön. */
    public boolean onBack() {
        synchronized (lock) {
            if (state == TITLE) return false;
            if (state == PLAYING && !paused) {
                paused = true;
                return true;
            }
            goTitle();
            return true;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) { surfaceReady = true; }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) {
        synchronized (lock) {
            boolean first = W == 0;
            W = w;
            H = hh;
            U = Math.min(W, H) / 100f;
            hudH = 12 * U;
            fLeft = 4 * U;
            fRight = W - 4 * U;
            fTop = hudH + 7 * U;
            fBottom = H - 4 * U;
            btnR = 9 * U;
            fishBx = W - 13 * U;
            fishBy = H - 13 * U;
            dashBx = W - 35 * U;
            dashBy = H - 13 * U;
            pauseBx = W - 7 * U;
            pauseBy = hudH / 2;
            bgTheme = -1;
            if (first) goTitle();
            else if (state == PLAYING || state == LEVEL_DONE) clampEverything();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) { surfaceReady = false; }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1e9f;
            last = now;
            if (dt > 0.05f) dt = 0.05f;
            if (!surfaceReady || W == 0) {
                sleepMs(30);
                continue;
            }
            Canvas c = null;
            try {
                c = (Build.VERSION.SDK_INT >= 26) ? holder.lockHardwareCanvas() : holder.lockCanvas();
                if (c != null) {
                    synchronized (lock) {
                        update(dt);
                        render(c);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) {
                    try { holder.unlockCanvasAndPost(c); } catch (Exception ignored) { }
                }
            }
            long spent = (System.nanoTime() - now) / 1000000L;
            if (spent < 15) sleepMs(15 - spent);
        }
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    // =====================================================================
    //  Oyun akışı
    // =====================================================================

    private void goTitle() {
        state = TITLE;
        stateTime = 0;
        paused = false;
        touching = false;
        parts.clear();
        texts.clear();
        for (int i = 0; i < 3; i++) { bubble[i] = null; bubbleT[i] = 0; }
        ensureBg(0);
    }

    private void newGame() {
        level = 1;
        score = 0;
        fishCount = 0;
        startLevel();
    }

    private int theme() { return (level - 1) % 3; }

    private void startLevel() {
        state = PLAYING;
        stateTime = 0;
        paused = false;
        catches = 0;
        target = Math.min(3 + (level - 1) / 2, 6);
        timeLeft = 55 + 5 * target;
        fishCount = Math.min(fishCount + 2, 5);
        mBoost = 0;
        dashT = 0;
        dashCd = 0;
        jumpCd = 3;
        fishOn = false;
        puOn = false;
        puSpawn = 8;
        touching = false;
        parts.clear();
        texts.clear();
        for (int i = 0; i < 3; i++) { bubble[i] = null; bubbleT[i] = 0; }

        mx = fLeft + 12 * U;
        my = fBottom - 6 * U;
        bx = fRight - 45 * U;
        by = fBottom - 6 * U;
        cx = (fLeft + fRight) / 2;
        cy = fTop + (fBottom - fTop) * 0.35f;
        cvx = cvy = 0;
        stamina = 1;
        catState = CAT_RUN;
        hideBox = -1;
        wanderAng = rnd.nextFloat() * 6.28f;

        buildObstacles();
        for (int i = 0; i < bfx.length; i++) {
            bfx[i] = rand(fLeft, fRight);
            bfy[i] = rand(fTop, fBottom);
            bfa[i] = rnd.nextFloat() * 6.28f;
            bfp[i] = rnd.nextFloat() * 6.28f;
        }
        ensureBg(theme());
        say(SPK_MIH, level == 1 ? "Pamuk kaçtı! Yakalayalım!" : "Hadi baba, yine kaçtı!");
        say(SPK_CAT, "Miyav!");
        snd.meow();
    }

    private void buildObstacles() {
        obstacles.clear();
        int solid = Math.min(3 + level, 9);
        int boxes = level >= 2 ? (level >= 4 ? 2 : 1) : 0;
        int tries = 0;
        while (obstacles.size() < solid + boxes && tries < 400) {
            tries++;
            boolean isBox = obstacles.size() >= solid;
            int type;
            float r;
            if (isBox) { type = OB_BOX; r = 5 * U; }
            else {
                int k = rnd.nextInt(10);
                if (k < 5) { type = OB_BUSH; r = rand(5, 7.5f) * U; }
                else if (k < 8) { type = OB_TREE; r = 3.2f * U; }
                else { type = OB_POT; r = 3 * U; }
            }
            float x = rand(fLeft + 14 * U, fRight - 14 * U);
            float y = rand(fTop + 10 * U, fBottom - 8 * U);
            if (dist(x, y, mx, my) < 18 * U || dist(x, y, bx, by) < 18 * U || dist(x, y, cx, cy) < 14 * U) continue;
            if (dist(x, y, fishBx, fishBy) < 20 * U || dist(x, y, dashBx, dashBy) < 20 * U) continue;
            boolean ok = true;
            for (float[] o : obstacles) {
                if (dist(x, y, o[0], o[1]) < o[2] + r + 12 * U) { ok = false; break; }
            }
            if (!ok) continue;
            obstacles.add(new float[]{x, y, r, type, rnd.nextFloat()});
        }
    }

    private void clampEverything() {
        mx = clamp(mx, fLeft, fRight); my = clamp(my, fTop, fBottom);
        bx = clamp(bx, fLeft, fRight); by = clamp(by, fTop, fBottom);
        cx = clamp(cx, fLeft, fRight); cy = clamp(cy, fTop, fBottom);
        ensureBg(theme());
    }

    // =====================================================================
    //  Güncelleme
    // =====================================================================

    private void update(float dt) {
        globalTime += dt;
        stateTime += dt;
        updateParticles(dt);
        for (int i = 0; i < 3; i++) if (bubbleT[i] > 0) bubbleT[i] -= dt;

        if (state == TITLE) {
            updateTitleDemo(dt);
            return;
        }
        if (state != PLAYING || paused) return;

        updateButterflies(dt);
        updateMihrimah(dt);
        updateBaba(dt);
        updateCat(dt);
        updatePowerups(dt);
        updateChatter(dt);

        if (catState != CAT_CAUGHT) {
            timeLeft -= dt;
            if (timeLeft <= 10 && (int) (timeLeft + dt) != (int) timeLeft && timeLeft > 0) snd.ding();
            if (timeLeft <= 0) {
                timeLeft = 0;
                state = GAME_OVER;
                stateTime = 0;
                touching = false;
                if (score > best) {
                    best = score;
                    prefs.edit().putInt("best", best).apply();
                }
                snd.sad();
            }
        }
    }

    private void updateMihrimah(float dt) {
        float speed = 36 * U * (mBoost > 0 ? 1.5f : 1f);
        if (mBoost > 0) {
            mBoost -= dt;
            if (rnd.nextFloat() < 0.4f) spawn(1, mx + rand(-3, 3) * U, my - rand(0, 10) * U, 0xFFFFE14D, 1.2f);
        }
        mMoving = false;
        if (touching && catState != CAT_CAUGHT) {
            float dx = tx - mx, dy = ty - my;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 1.5f * U) {
                float step = Math.min(d, speed * dt);
                mx += dx / d * step;
                my += dy / d * step;
                if (Math.abs(dx) > 0.5f * U) mFace = dx > 0 ? 1 : -1;
                mMoving = true;
            }
        }
        float[] r = collide(mx, my, 4 * U);
        mx = r[0]; my = r[1];
        if (mMoving) {
            mPhase += dt * 14;
            if (rnd.nextFloat() < 0.1f) spawn(2, mx, my, 0x88C8B89A, 0.5f);
        }
    }

    private void updateBaba(float dt) {
        if (dashCd > 0) dashCd -= dt;
        float speed = (27 + Math.min(level, 8)) * U;
        if (dashT > 0) {
            dashT -= dt;
            speed = 66 * U;
            if (rnd.nextFloat() < 0.6f) spawn(2, bx, by, 0x99FFFFFF, 0.6f);
        }
        bMoving = false;
        if (catState == CAT_CAUGHT) return;

        float gx, gy;
        if (catState == CAT_HIDE && hideBox >= 0 && hideBox < obstacles.size()) {
            float[] o = obstacles.get(hideBox);
            gx = o[0]; gy = o[1];
        } else if (catState == CAT_EAT || dashT > 0) {
            gx = cx; gy = cy;
        } else {
            // Mihrimah'ın tam karşı tarafına geçip kediyi arada sıkıştır.
            float dmx = cx - mx, dmy = cy - my;
            float dm = (float) Math.sqrt(dmx * dmx + dmy * dmy) + 0.001f;
            float lead = 0.35f;
            if (dm < 55 * U) {
                gx = cx + dmx / dm * 14 * U + cvx * lead;
                gy = cy + dmy / dm * 14 * U + cvy * lead;
            } else {
                gx = cx + cvx * lead;
                gy = cy + cvy * lead;
            }
            // Kediye çok yakınsa doğrudan atıl.
            if (dist(bx, by, cx, cy) < 16 * U) { gx = cx; gy = cy; }
            gx = clamp(gx, fLeft, fRight);
            gy = clamp(gy, fTop, fBottom);
        }
        float dx = gx - bx, dy = gy - by;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > 1.5f * U) {
            float step = Math.min(d, speed * dt);
            bx += dx / d * step;
            by += dy / d * step;
            if (Math.abs(dx) > 0.5f * U) bFace = dx > 0 ? 1 : -1;
            bMoving = true;
            bPhase += dt * (dashT > 0 ? 20 : 11);
        }
        float[] r = collide(bx, by, 4.5f * U);
        bx = r[0]; by = r[1];
    }

    private void updateCat(float dt) {
        tailPhase += dt * (catState == CAT_RUN ? 9 : 4);
        if (jumpCd > 0) jumpCd -= dt;
        if (meowCd > 0) meowCd -= dt;
        float dM = dist(cx, cy, mx, my), dB = dist(cx, cy, bx, by);
        float catchD = 9 * U;

        switch (catState) {
            case CAT_CAUGHT: {
                catTimer -= dt;
                float hx = catcher == SPK_MIH ? mx : bx;
                float hy = catcher == SPK_MIH ? my - 6 * U : by - 9 * U;
                float k = Math.min(1, (1.4f - catTimer) * 5);
                cx = catchFromX + (hx - catchFromX) * k;
                cy = catchFromY + (hy - catchFromY) * k;
                if (rnd.nextFloat() < 0.25f) spawn(0, cx + rand(-4, 4) * U, cy - rand(2, 6) * U, 0xFFFF5C8A, 1.2f);
                if (catTimer <= 0) {
                    if (catches >= target) {
                        levelBonus = (int) timeLeft * 10;
                        score += (int) levelBonus;
                        state = LEVEL_DONE;
                        stateTime = 0;
                        touching = false;
                        if (score > best) {
                            best = score;
                            prefs.edit().putInt("best", best).apply();
                        }
                        snd.fanfare();
                        for (int i = 0; i < 40; i++) spawn(1, rand(0, W), rand(0, H * 0.6f), randColor(), 2f);
                    } else {
                        respawnCat();
                    }
                }
                return;
            }
            case CAT_EAT: {
                catTimer -= dt;
                cvx *= 0.8f;
                cvy *= 0.8f;
                if (dM < catchD) { caught(SPK_MIH); return; }
                if (dB < catchD) { caught(SPK_BABA); return; }
                // Çok yaklaşılırsa yemeği bırakıp kaçar
                if (catTimer <= 0 || (Math.min(dM, dB) < 14 * U && catTimer < 1.2f)) {
                    catState = CAT_RUN;
                    stamina = Math.max(stamina, 0.6f);
                    say(SPK_CAT, "Nyam! Şimdi kaç!");
                }
                return;
            }
            case CAT_HIDE: {
                catTimer -= dt;
                float[] o = obstacles.get(hideBox);
                cx = o[0];
                cy = o[1];
                float dmb = dist(mx, my, o[0], o[1]), dbb = dist(bx, by, o[0], o[1]);
                if (dmb < o[2] + 4 * U) { caught(SPK_MIH); say(SPK_MIH, "Kutuda yakaladım!"); return; }
                if (dbb < o[2] + 4 * U) { caught(SPK_BABA); say(SPK_BABA, "Kutudan çıkamazsın!"); return; }
                boolean spooked = Math.min(dmb, dbb) < o[2] + 12 * U && rnd.nextFloat() < dt * 1.5f;
                if (catTimer <= 0 || spooked) {
                    catState = CAT_RUN;
                    hideBox = -1;
                    stamina = 1;
                    float ang = rnd.nextFloat() * 6.28f;
                    cvx = (float) Math.cos(ang) * 40 * U;
                    cvy = (float) Math.sin(ang) * 40 * U;
                    say(SPK_CAT, spooked ? "Böö! Miyav!" : "Miyav!");
                    snd.meow();
                    for (int i = 0; i < 8; i++) spawn(2, cx, cy, 0xAAC89A6A, 0.8f);
                }
                return;
            }
            case CAT_JUMP: {
                catTimer -= dt;
                cx += jumpVx * dt;
                cy += jumpVy * dt;
                cx = clamp(cx, fLeft, fRight);
                cy = clamp(cy, fTop, fBottom);
                if (catTimer <= 0) {
                    catState = CAT_RUN;
                    float[] r = collide(cx, cy, 3.5f * U);
                    cx = r[0]; cy = r[1];
                    for (int i = 0; i < 6; i++) spawn(2, cx, cy, 0xAAFFFFFF, 0.6f);
                }
                return;
            }
            default:
                break;
        }

        // ---- CAT_RUN ----
        if (dM < catchD) { caught(SPK_MIH); return; }
        if (dB < catchD) { caught(SPK_BABA); return; }

        float ax = 0, ay = 0, fear = 0;
        float fleeR = 42 * U;
        float[][] chasers = {{mx, my}, {bx, by}};
        float axAway = 0, ayAway = 0;
        for (float[] ch : chasers) {
            float dx = cx - ch[0], dy = cy - ch[1];
            float d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
            if (d < fleeR) {
                float w = (fleeR - d) / fleeR;
                fear = Math.max(fear, w);
                ax += dx / d * w * 2.2f;
                ay += dy / d * w * 2.2f;
                axAway += dx / d;
                ayAway += dy / d;
            }
        }
        // Duvarlardan uzak dur
        float margin = 13 * U;
        if (cx < fLeft + margin) ax += (fLeft + margin - cx) / margin * 1.6f;
        if (cx > fRight - margin) ax -= (cx - (fRight - margin)) / margin * 1.6f;
        if (cy < fTop + margin) ay += (fTop + margin - cy) / margin * 1.6f;
        if (cy > fBottom - margin) ay -= (cy - (fBottom - margin)) / margin * 1.6f;
        // Engellerden kaç, kutuya saklanmayı düşün
        for (int i = 0; i < obstacles.size(); i++) {
            float[] o = obstacles.get(i);
            float dx = cx - o[0], dy = cy - o[1];
            float d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
            if ((int) o[3] == OB_BOX) {
                if (d < o[2] + 2 * U && fear > 0.3f && rnd.nextFloat() < dt * 3) {
                    catState = CAT_HIDE;
                    hideBox = i;
                    catTimer = rand(2.5f, 4.5f);
                    say(SPK_CAT, "...");
                    say(SPK_MIH, "Kutuya girdi!");
                    snd.whoosh();
                    return;
                }
                if (fear > 0.3f && d < 30 * U) { ax -= dx / d * 0.9f; ay -= dy / d * 0.9f; }
                continue;
            }
            float lim = o[2] + 9 * U;
            if (d < lim) {
                ax += dx / d * (lim - d) / lim * 2f;
                ay += dy / d * (lim - d) / lim * 2f;
            }
        }
        // Balık kokusu
        if (fishOn) {
            float df = dist(cx, cy, fishX, fishY);
            if (df < 55 * U && fear < 0.65f) {
                ax += (fishX - cx) / df * 2.5f;
                ay += (fishY - cy) / df * 2.5f;
                if (df < 3.5f * U) {
                    fishOn = false;
                    catState = CAT_EAT;
                    catTimer = 2.8f;
                    say(SPK_CAT, "Nyam nyam!");
                    snd.purr();
                    return;
                }
            }
        }
        // Kelebek kovalama
        if (fear < 0.25f) {
            int bi = -1;
            float bd = 30 * U;
            for (int i = 0; i < bfx.length; i++) {
                float d = dist(cx, cy, bfx[i], bfy[i]);
                if (d < bd) { bd = d; bi = i; }
            }
            if (bi >= 0) {
                ax += (bfx[bi] - cx) / (bd + 1) * 0.8f;
                ay += (bfy[bi] - cy) / (bd + 1) * 0.8f;
                if (meowCd <= 0 && rnd.nextFloat() < dt * 0.3f) {
                    say(SPK_CAT, theme() == 2 ? "Ateş böceği!" : "Kelebek!");
                    meowCd = 5;
                }
            }
        }
        // Rastgele gezinme
        wanderAng += rand(-2.5f, 2.5f) * dt;
        ax += (float) Math.cos(wanderAng) * 0.35f;
        ay += (float) Math.sin(wanderAng) * 0.35f;

        // Sıkışınca zıpla (2. seviyeden sonra)
        if (level >= 2 && jumpCd <= 0 && dM < 17 * U && dB < 17 * U && stamina > 0.35f) {
            float ex = (fLeft + fRight) / 2 - cx, ey = (fTop + fBottom) / 2 - cy;
            float el = (float) Math.sqrt(ex * ex + ey * ey) + 0.001f;
            float al = (float) Math.sqrt(axAway * axAway + ayAway * ayAway) + 0.001f;
            float jx = ex / el + axAway / al * 0.6f, jy = ey / el + ayAway / al * 0.6f;
            float jl = (float) Math.sqrt(jx * jx + jy * jy) + 0.001f;
            catState = CAT_JUMP;
            catTimer = 0.45f;
            jumpVx = jx / jl * 70 * U;
            jumpVy = jy / jl * 70 * U;
            jumpCd = Math.max(3.5f, 8 - level * 0.5f);
            stamina -= 0.3f;
            say(SPK_CAT, "Hop!");
            say(SPK_BABA, "Vay, zıpladı!");
            snd.whoosh();
            return;
        }

        float sprint = Math.min(40 + 3 * (level - 1), 58) * U;
        float speed;
        if (fear > 0.05f) {
            speed = stamina > 0.12f ? sprint : 24 * U;
            stamina -= dt * 0.2f * fear;
        } else {
            speed = 15 * U;
            stamina += dt * 0.14f;
        }
        stamina = clamp(stamina, 0, 1);
        float al = (float) Math.sqrt(ax * ax + ay * ay) + 0.0001f;
        float k = Math.min(1, dt * 7);
        cvx += (ax / al * speed - cvx) * k;
        cvy += (ay / al * speed - cvy) * k;
        cx += cvx * dt;
        cy += cvy * dt;
        float[] r = collide(cx, cy, 3.5f * U);
        cx = r[0]; cy = r[1];
        if (Math.abs(cvx) > 2 * U) cFace = cvx > 0 ? 1 : -1;
        legPhase += dt * (Math.abs(cvx) + Math.abs(cvy)) / U * 0.5f;

        if (fear > 0.6f && meowCd <= 0 && rnd.nextFloat() < dt * 0.8f) {
            String[] lines = {"Yakalayamazsınız!", "Miyav!", "Hıh!", "Tuu tuu!", "Beni kimse tutamaz!"};
            say(SPK_CAT, lines[rnd.nextInt(lines.length)]);
            meowCd = 4;
            if (rnd.nextBoolean()) snd.meow();
        }
    }

    private void caught(int who) {
        catState = CAT_CAUGHT;
        catcher = who;
        catTimer = 1.4f;
        catchFromX = cx;
        catchFromY = cy;
        catches++;
        score += 100;
        hideBox = -1;
        touching = false;
        snd.purr();
        snd.pop();
        addText("+100", cx, cy - 8 * U, 0xFFFFE14D);
        for (int i = 0; i < 18; i++) spawn(0, cx, cy - 4 * U, 0xFFFF5C8A, 1.4f);
        if (who == SPK_MIH) {
            String[] l = {"Yakaladım!", "Gel bakalım Pamuk!", "Seni seviyorum Pamuk!"};
            say(SPK_MIH, l[rnd.nextInt(l.length)]);
            say(SPK_BABA, "Aferin kızıma!");
        } else {
            String[] l = {"Yakaladım!", "Tuttum onu!", "Hop, kucağıma!"};
            say(SPK_BABA, l[rnd.nextInt(l.length)]);
            say(SPK_MIH, "Yaşasın babam!");
        }
        say(SPK_CAT, "Mırr mırr...");
    }

    private void respawnCat() {
        float bestX = cx, bestY = cy, bestD = -1;
        for (int i = 0; i < 30; i++) {
            float x = rand(fLeft + 10 * U, fRight - 10 * U);
            float y = rand(fTop + 8 * U, fBottom - 8 * U);
            float[] r = collide(x, y, 4 * U);
            x = r[0]; y = r[1];
            float d = Math.min(dist(x, y, mx, my), dist(x, y, bx, by));
            if (d > bestD) { bestD = d; bestX = x; bestY = y; }
        }
        for (int i = 0; i < 12; i++) spawn(1, cx, cy, 0xFFFFFFFF, 0.8f);
        cx = bestX;
        cy = bestY;
        cvx = cvy = 0;
        stamina = 1;
        catState = CAT_RUN;
        jumpCd = 2;
        for (int i = 0; i < 12; i++) spawn(1, cx, cy, 0xFFFFE14D, 0.8f);
        say(SPK_CAT, "Hop! Yine kaçtım!");
        say(SPK_MIH, "Babaa, yine kaçtı!");
        snd.meow();
    }

    private void updatePowerups(float dt) {
        if (fishOn) {
            fishLife -= dt;
            if (fishLife <= 0) fishOn = false;
        }
        if (puOn) {
            puLife -= dt;
            if (puLife <= 0) puOn = false;
            else if (dist(mx, my, puX, puY) < 7 * U || dist(bx, by, puX, puY) < 7 * U) {
                puOn = false;
                snd.ding();
                for (int i = 0; i < 14; i++) spawn(1, puX, puY, puType == 0 ? 0xFFFFE14D : 0xFF7FD4FF, 1f);
                if (puType == 0) {
                    mBoost = 5;
                    addText("Hız!", mx, my - 16 * U, 0xFFFFE14D);
                    say(SPK_MIH, "Rüzgâr gibiyim!");
                } else {
                    fishCount = Math.min(fishCount + 1, 5);
                    addText("+1 Balık", puX, puY - 6 * U, 0xFF7FD4FF);
                }
            }
        } else {
            puSpawn -= dt;
            if (puSpawn <= 0) {
                puSpawn = rand(10, 15);
                for (int i = 0; i < 20; i++) {
                    float x = rand(fLeft + 10 * U, fRight - 10 * U);
                    float y = rand(fTop + 8 * U, fBottom - 8 * U);
                    if (freeSpot(x, y, 8 * U) && dist(x, y, fishBx, fishBy) > 18 * U && dist(x, y, dashBx, dashBy) > 18 * U) {
                        puX = x; puY = y; puOn = true; puLife = 8; puType = rnd.nextInt(3) == 0 ? 1 : 0;
                        break;
                    }
                }
            }
        }
    }

    private void updateButterflies(float dt) {
        for (int i = 0; i < bfx.length; i++) {
            bfa[i] += rand(-2, 2) * dt;
            float sp = 12 * U;
            bfx[i] += (float) Math.cos(bfa[i]) * sp * dt;
            bfy[i] += (float) Math.sin(bfa[i]) * sp * dt;
            bfp[i] += dt * 18;
            if (bfx[i] < fLeft || bfx[i] > fRight) { bfa[i] = (float) Math.PI - bfa[i]; bfx[i] = clamp(bfx[i], fLeft, fRight); }
            if (bfy[i] < fTop || bfy[i] > fBottom) { bfa[i] = -bfa[i]; bfy[i] = clamp(bfy[i], fTop, fBottom); }
        }
    }

    private void updateChatter(float dt) {
        chatterCd -= dt;
        if (chatterCd > 0 || catState == CAT_CAUGHT) return;
        chatterCd = rand(5, 9);
        if (rnd.nextBoolean()) {
            String[] l = {"Babaa, öbür taraftan dolaş!", "Pamuk, gel buraya!", "Köşeye sıkıştıralım!",
                    "Çok hızlı bu kedi!", "Balık atayım mı?"};
            say(SPK_MIH, l[rnd.nextInt(l.length)]);
        } else {
            String[] l = {"Ben bu taraftayım kızım!", "Sağdan dolaşıyorum!", "Kaçma Pamuk!",
                    "Nefes nefese kaldım!", "Beraber yaparız!"};
            say(SPK_BABA, l[rnd.nextInt(l.length)]);
        }
    }

    private void dropFish() {
        if (fishCount <= 0 || fishOn || catState == CAT_CAUGHT) {
            if (fishCount <= 0) say(SPK_MIH, "Balığım kalmadı!");
            return;
        }
        fishCount--;
        fishOn = true;
        fishLife = 9;
        fishX = mx + mFace * 5 * U;
        fishY = my;
        float[] r = collide(fishX, fishY, 2 * U);
        fishX = clamp(r[0], fLeft, fRight);
        fishY = clamp(r[1], fTop, fBottom);
        snd.pop();
        say(SPK_MIH, "Pamuk! Balık burada!");
        for (int i = 0; i < 8; i++) spawn(1, fishX, fishY, 0xFF7FD4FF, 0.8f);
    }

    private void doDash() {
        if (dashCd > 0 || catState == CAT_CAUGHT) return;
        dashT = 1.1f;
        dashCd = 6;
        snd.whoosh();
        String[] l = {"Geliyorum kızım!", "Baba turbo!", "Hızlı koşu modu!"};
        say(SPK_BABA, l[rnd.nextInt(l.length)]);
    }

    // ---- Başlık ekranı canlandırması ----
    private void updateTitleDemo(float dt) {
        float t = globalTime * 0.5f;
        float ccx = W / 2f, ccy = H * 0.89f, rx = W * 0.4f, ry = H * 0.05f;
        cx = ccx + (float) Math.cos(t) * rx;
        cy = ccy + (float) Math.sin(t) * ry;
        cFace = -Math.sin(t) > 0 ? 1 : -1;
        mx = ccx + (float) Math.cos(t - 0.55f) * rx;
        my = ccy + (float) Math.sin(t - 0.55f) * ry;
        mFace = cFace;
        bx = ccx + (float) Math.cos(t - 1.0f) * rx;
        by = ccy + (float) Math.sin(t - 1.0f) * ry;
        bFace = cFace;
        mPhase += dt * 14;
        bPhase += dt * 11;
        tailPhase += dt * 9;
        legPhase += dt * 14;
        catState = CAT_RUN;
        mMoving = bMoving = true;
        if (rnd.nextFloat() < dt * 0.4f) {
            String[] l = {"Miyav!", "Yakalayın beni!", "Tuu tuu!"};
            say(SPK_CAT, l[rnd.nextInt(l.length)]);
        }
    }

    // =====================================================================
    //  Dokunma
    // =====================================================================

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        synchronized (lock) {
            int a = e.getActionMasked();
            int idx = e.getActionIndex();
            switch (a) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    onDown(e.getX(idx), e.getY(idx), e.getPointerId(idx));
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (touching) {
                        int pi = e.findPointerIndex(movePointer);
                        if (pi >= 0) { tx = e.getX(pi); ty = e.getY(pi); }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    if (e.getPointerId(idx) == movePointer) { touching = false; movePointer = -1; }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    touching = false;
                    movePointer = -1;
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    private void onDown(float x, float y, int pid) {
        switch (state) {
            case TITLE:
                if (soundBtn.contains(x, y)) {
                    snd.enabled = !snd.enabled;
                    prefs.edit().putBoolean("sound", snd.enabled).apply();
                    snd.ding();
                } else if (playBtn.contains(x, y) && stateTime > 0.3f) {
                    newGame();
                }
                break;
            case PLAYING:
                if (paused) {
                    if (menuBtn.contains(x, y)) goTitle();
                    else paused = false;
                    return;
                }
                if (dist(x, y, pauseBx, pauseBy) < 7 * U) { paused = true; return; }
                if (dist(x, y, fishBx, fishBy) < btnR * 1.15f) { dropFish(); return; }
                if (dist(x, y, dashBx, dashBy) < btnR * 1.15f) { doDash(); return; }
                if (!touching) {
                    touching = true;
                    everTouched = true;
                    movePointer = pid;
                    tx = x;
                    ty = y;
                }
                break;
            case LEVEL_DONE:
                if (stateTime > 1f) {
                    level++;
                    startLevel();
                }
                break;
            case GAME_OVER:
                if (stateTime < 0.8f) return;
                if (againBtn.contains(x, y)) newGame();
                else if (menuBtn.contains(x, y)) goTitle();
                break;
            default:
                break;
        }
    }

    // =====================================================================
    //  Yardımcılar
    // =====================================================================

    private float[] collide(float x, float y, float r) {
        for (int pass = 0; pass < 2; pass++) {
            for (float[] o : obstacles) {
                if ((int) o[3] == OB_BOX) continue;
                float dx = x - o[0], dy = y - o[1];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float min = o[2] + r;
                if (d < min) {
                    if (d < 0.001f) { dx = 1; dy = 0; d = 1; }
                    x = o[0] + dx / d * min;
                    y = o[1] + dy / d * min;
                }
            }
        }
        return new float[]{clamp(x, fLeft, fRight), clamp(y, fTop, fBottom)};
    }

    private boolean freeSpot(float x, float y, float r) {
        for (float[] o : obstacles) if (dist(x, y, o[0], o[1]) < o[2] + r) return false;
        return true;
    }

    private void say(int who, String s) {
        bubble[who] = s;
        bubbleT[who] = 2.2f;
    }

    private void addText(String s, float x, float y, int color) {
        FT f = new FT();
        f.s = s; f.x = x; f.y = y; f.life = 1.2f; f.color = color;
        texts.add(f);
    }

    /** type: 0 kalp, 1 yıldız parıltısı, 2 toz */
    private void spawn(int type, float x, float y, int color, float life) {
        if (parts.size() > 300) return;
        P q = new P();
        q.type = type; q.x = x; q.y = y; q.color = color;
        q.life = q.max = life * rand(0.7f, 1.2f);
        float ang = rnd.nextFloat() * 6.28f;
        float spd = (type == 2 ? 6 : 20) * U * rand(0.3f, 1f);
        q.vx = (float) Math.cos(ang) * spd;
        q.vy = (float) Math.sin(ang) * spd - (type == 0 ? 15 * U : 0);
        q.size = (type == 2 ? rand(1f, 2f) : rand(1.2f, 2.4f)) * U;
        parts.add(q);
    }

    private void updateParticles(float dt) {
        for (int i = parts.size() - 1; i >= 0; i--) {
            P q = parts.get(i);
            q.life -= dt;
            if (q.life <= 0) { parts.remove(i); continue; }
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            q.vx *= 0.96f;
            q.vy = q.vy * 0.96f + (q.type == 0 ? -4 * U * dt : 0);
        }
        for (int i = texts.size() - 1; i >= 0; i--) {
            FT f = texts.get(i);
            f.life -= dt;
            f.y -= 12 * U * dt;
            if (f.life <= 0) texts.remove(i);
        }
    }

    private float rand(float a, float b) { return a + rnd.nextFloat() * (b - a); }

    private int randColor() {
        int[] c = {0xFFFF5C8A, 0xFFFFE14D, 0xFF7FD4FF, 0xFF8BE36B, 0xFFC7A6FF, 0xFFFFA64D};
        return c[rnd.nextInt(c.length)];
    }

    private static float clamp(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // =====================================================================
    //  Çizim
    // =====================================================================

    private void render(Canvas c) {
        if (state == TITLE) {
            drawTitle(c);
            return;
        }
        int th = theme();
        ensureBg(th);
        c.drawBitmap(bg, 0, 0, null);

        // Fener ve kelebekler ile gölgeler
        if (fishOn) drawFish(c, fishX, fishY, U * 1.1f, (float) Math.sin(globalTime * 6) * 0.1f, fishLife < 2 && ((int) (globalTime * 8)) % 2 == 0);
        if (puOn) drawPowerup(c);

        drawWorldSorted(c);

        if (th != 2) for (int i = 0; i < bfx.length; i++) drawButterfly(c, bfx[i], bfy[i], bfp[i], bfColor[i]);

        if (th == 1) {
            p.setShader(null);
            p.setColor(0x33FF8A3D);
            c.drawRect(0, 0, W, H, p);
        } else if (th == 2) {
            drawNight(c);
        }

        drawParticles(c);
        drawBubbles(c);
        drawHud(c);
        drawButtons(c);

        if (state == PLAYING && level == 1 && !everTouched && stateTime < 8) drawHint(c);
        if (paused && state == PLAYING) drawPause(c);
        if (state == LEVEL_DONE) drawLevelDone(c);
        if (state == GAME_OVER) drawGameOver(c);
    }

    private void drawWorldSorted(Canvas c) {
        int n = 0;
        // 0..obstacles-1 -> engeller, 1000 Mihrimah, 1001 Baba, 1002 Kedi
        for (int i = 0; i < obstacles.size() && n < 60; i++) { order[n] = i; orderY[n] = obstacles.get(i)[1]; n++; }
        order[n] = 1000; orderY[n] = my; n++;
        order[n] = 1001; orderY[n] = by; n++;
        boolean catOnTop = catState == CAT_CAUGHT || catState == CAT_JUMP;
        if (catState != CAT_HIDE && !catOnTop) { order[n] = 1002; orderY[n] = cy; n++; }
        // ekleme sıralaması (küçük dizi)
        for (int i = 1; i < n; i++) {
            int o = order[i];
            float y = orderY[i];
            int j = i - 1;
            while (j >= 0 && orderY[j] > y) { order[j + 1] = order[j]; orderY[j + 1] = orderY[j]; j--; }
            order[j + 1] = o;
            orderY[j + 1] = y;
        }
        boolean happy = catState == CAT_CAUGHT;
        for (int i = 0; i < n; i++) {
            int o = order[i];
            if (o == 1000) drawMihrimah(c, mx, my, U, mPhase, mFace, mMoving, happy && catcher == SPK_MIH, catState == CAT_CAUGHT && catcher == SPK_MIH);
            else if (o == 1001) drawBaba(c, bx, by, U, bPhase, bFace, bMoving, happy && catcher == SPK_BABA, catState == CAT_CAUGHT && catcher == SPK_BABA);
            else if (o == 1002) drawCat(c, cx, cy, U, 0);
            else drawObstacle(c, obstacles.get(o), o);
        }
        if (catState == CAT_JUMP) {
            float t = 1 - catTimer / 0.45f;
            drawShadow(c, cx, cy, 4 * U, 1.4f * U, 0x44000000);
            drawCat(c, cx, cy, U, (float) Math.sin(Math.PI * t) * 12 * U);
        } else if (catState == CAT_CAUGHT) {
            drawCat(c, cx, cy, U, 0);
        }
    }

    private void ensureBg(int th) {
        if (W == 0) return;
        if (bg != null && bgTheme == th && bg.getWidth() == W && bg.getHeight() == H) return;
        bg = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        bgTheme = th;
        Canvas c = new Canvas(bg);
        Random r = new Random(42 + th);
        int g1, g2, tuft, fence, fenceDark;
        if (th == 0) { g1 = 0xFF9BE07A; g2 = 0xFF7FCB62; tuft = 0xFF5FAF4A; fence = 0xFFFFF4E0; fenceDark = 0xFFD9C6A5; }
        else if (th == 1) { g1 = 0xFFB9D67A; g2 = 0xFF96BE5E; tuft = 0xFF7A9E45; fence = 0xFFF2D6B3; fenceDark = 0xFFC9A57E; }
        else { g1 = 0xFF3F6B5E; g2 = 0xFF2F574B; tuft = 0xFF244338; fence = 0xFF8E9AAF; fenceDark = 0xFF5F6B80; }
        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setShader(new LinearGradient(0, 0, 0, H, g1, g2, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, bp);
        bp.setShader(null);

        // Çim desenli şeritler
        bp.setColor(0x10FFFFFF);
        for (float x = -H; x < W; x += 14 * U) {
            Path st = new Path();
            st.moveTo(x, H); st.lineTo(x + 7 * U, H); st.lineTo(x + 7 * U + H * 0.5f, 0); st.lineTo(x + H * 0.5f, 0); st.close();
            c.drawPath(st, bp);
        }
        // Taş yol
        bp.setColor(th == 2 ? 0xFF6B7285 : 0xFFE8DCC4);
        float py = fTop + (fBottom - fTop) * 0.55f;
        for (float x = -2 * U; x < W; x += 9 * U) {
            float yy = py + (float) Math.sin(x / (W / 3f)) * 10 * U;
            c.drawOval(new RectF(x, yy - 2.2f * U, x + 6.5f * U, yy + 2.2f * U), bp);
        }
        // Çim tutamları
        bp.setStyle(Paint.Style.STROKE);
        bp.setStrokeWidth(0.5f * U);
        bp.setStrokeCap(Paint.Cap.ROUND);
        bp.setColor(tuft);
        for (int i = 0; i < 160; i++) {
            float x = r.nextFloat() * W, y = hudH + r.nextFloat() * (H - hudH);
            c.drawLine(x, y, x - 0.8f * U, y - 1.8f * U, bp);
            c.drawLine(x, y, x, y - 2.2f * U, bp);
            c.drawLine(x, y, x + 0.8f * U, y - 1.8f * U, bp);
        }
        bp.setStyle(Paint.Style.FILL);
        // Çiçekler
        int[] fc = th == 2 ? new int[]{0xFF8FA3C8, 0xFFB7A6D6, 0xFF6F8BB0} : new int[]{0xFFFF7FAF, 0xFFFFE066, 0xFFFFFFFF, 0xFFB38BFF, 0xFFFF9F5A};
        for (int i = 0; i < 45; i++) {
            float x = r.nextFloat() * W, y = fTop + r.nextFloat() * (H - fTop);
            bp.setColor(fc[r.nextInt(fc.length)]);
            float s = (0.7f + r.nextFloat() * 0.5f) * U;
            for (int k = 0; k < 5; k++) {
                double a = k * Math.PI * 2 / 5;
                c.drawCircle(x + (float) Math.cos(a) * s, y + (float) Math.sin(a) * s, s * 0.8f, bp);
            }
            bp.setColor(0xFFFFC93C);
            c.drawCircle(x, y, s * 0.6f, bp);
        }
        // Çit
        float fy = hudH;
        bp.setColor(fenceDark);
        c.drawRect(0, fy + 2.5f * U, W, fy + 3.8f * U, bp);
        c.drawRect(0, fy + 6f * U, W, fy + 7.2f * U, bp);
        for (float x = U; x < W; x += 5 * U) {
            bp.setColor(fenceDark);
            Path pk = new Path();
            pk.moveTo(x, fy + 8.5f * U); pk.lineTo(x, fy + 1.4f * U); pk.lineTo(x + 1.6f * U, fy); pk.lineTo(x + 3.2f * U, fy + 1.4f * U); pk.lineTo(x + 3.2f * U, fy + 8.5f * U); pk.close();
            c.drawPath(pk, bp);
            bp.setColor(fence);
            Path pk2 = new Path();
            pk2.moveTo(x + 0.3f * U, fy + 8.2f * U); pk2.lineTo(x + 0.3f * U, fy + 1.5f * U); pk2.lineTo(x + 1.6f * U, fy + 0.4f * U); pk2.lineTo(x + 2.9f * U, fy + 1.5f * U); pk2.lineTo(x + 2.9f * U, fy + 8.2f * U); pk2.close();
            c.drawPath(pk2, bp);
        }
        // Güneş/ay köşesi süsü
        if (th == 2) {
            bp.setColor(0xFFFFF6C8);
            for (int i = 0; i < 25; i++) c.drawCircle(r.nextFloat() * W, r.nextFloat() * hudH, 0.3f * U, bp);
        }
    }

    private void drawNight(Canvas c) {
        float rad = 38 * U;
        p.setShader(new RadialGradient(mx, my - 6 * U, rad, new int[]{0x00000000, 0x00000000, 0xB0081428},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        // Babanın feneri de biraz aydınlatır
        p.setShader(new RadialGradient(bx, by - 6 * U, 22 * U, 0x30FFF2B0, 0x00FFF2B0, Shader.TileMode.CLAMP));
        c.drawCircle(bx, by - 6 * U, 22 * U, p);
        p.setShader(null);
        // Pamuk'un parlayan gözleri
        if (catState == CAT_RUN || catState == CAT_JUMP) {
            float lift = catState == CAT_JUMP ? (float) Math.sin(Math.PI * (1 - catTimer / 0.45f)) * 12 * U : 0;
            float hx = cx + cFace * 3.6f * U, hy = cy - 6 * U - lift;
            p.setColor(0xFFE8FF6A);
            c.drawCircle(hx - 0.9f * U, hy, 0.55f * U, p);
            c.drawCircle(hx + 0.9f * U, hy, 0.55f * U, p);
        } else if (catState == CAT_HIDE && hideBox >= 0) {
            float[] o = obstacles.get(hideBox);
            p.setColor(0xFFE8FF6A);
            float bl = ((int) (globalTime * 2)) % 3 == 0 ? 0.15f : 0.5f;
            c.drawOval(new RectF(o[0] - 1.6f * U, o[1] - 5.2f * U - bl * U, o[0] - 0.6f * U, o[1] - 5.2f * U + bl * U), p);
            c.drawOval(new RectF(o[0] + 0.6f * U, o[1] - 5.2f * U - bl * U, o[0] + 1.6f * U, o[1] - 5.2f * U + bl * U), p);
        }
        // Ateş böcekleri
        for (int i = 0; i < bfx.length; i++) {
            float a = 0.5f + 0.5f * (float) Math.sin(bfp[i] * 0.3f);
            p.setShader(new RadialGradient(bfx[i], bfy[i] - 8 * U, 3 * U, Color.argb((int) (200 * a), 220, 255, 120), 0x00DCFF78, Shader.TileMode.CLAMP));
            c.drawCircle(bfx[i], bfy[i] - 8 * U, 3 * U, p);
            p.setShader(null);
        }
    }

    // ---- Karakterler ----

    private void drawShadow(Canvas c, float x, float y, float rx, float ry, int color) {
        p.setColor(color);
        rf.set(x - rx, y - ry, x + rx, y + ry);
        c.drawOval(rf, p);
    }

    private void drawMihrimah(Canvas c, float x, float y, float s, float ph, int face, boolean moving, boolean happy, boolean holding) {
        drawShadow(c, x, y, 3.6f * s, 1.2f * s, 0x40000000);
        float bob = moving ? Math.abs((float) Math.sin(ph)) * 0.8f * s : 0;
        float l1 = moving ? (float) Math.sin(ph) * 1.1f * s : 0;
        c.save();
        c.translate(x, y - bob);
        // Bacaklar
        p.setColor(0xFFFFD9B8);
        c.drawRect(-1.5f * s, -4.5f * s + Math.max(0, l1), -0.5f * s, -0.6f * s + Math.max(0, l1) * 0.3f, p);
        c.drawRect(0.5f * s, -4.5f * s + Math.max(0, -l1), 1.5f * s, -0.6f * s + Math.max(0, -l1) * 0.3f, p);
        p.setColor(0xFFE0457B);
        rf.set(-2.1f * s, -1.2f * s - Math.max(0, l1) * 0.2f, -0.2f * s, 0.2f * s); c.drawOval(rf, p);
        rf.set(0.2f * s, -1.2f * s - Math.max(0, -l1) * 0.2f, 2.1f * s, 0.2f * s); c.drawOval(rf, p);
        // Elbise
        path.reset();
        path.moveTo(-2f * s, -10.2f * s);
        path.lineTo(2f * s, -10.2f * s);
        path.lineTo(4.1f * s, -3.6f * s);
        path.quadTo(0, -2.8f * s, -4.1f * s, -3.6f * s);
        path.close();
        p.setColor(0xFFFF6FA8);
        c.drawPath(path, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(-1.6f * s, -5.2f * s, 0.45f * s, p);
        c.drawCircle(1.4f * s, -6.2f * s, 0.45f * s, p);
        c.drawCircle(0f * s, -4.4f * s, 0.45f * s, p);
        c.drawCircle(2.6f * s, -4.5f * s, 0.4f * s, p);
        c.drawCircle(-0.4f * s, -7.6f * s, 0.4f * s, p);
        // Yaka
        p.setColor(0xFFFFFFFF);
        rf.set(-1.8f * s, -10.6f * s, 1.8f * s, -9.2f * s); c.drawOval(rf, p);
        // Kollar
        sp.setStrokeWidth(1.1f * s);
        sp.setColor(0xFFFFD9B8);
        if (holding || happy) {
            c.drawLine(-1.9f * s, -9.4f * s, -2.4f * s, -8f * s, sp);
            c.drawLine(1.9f * s, -9.4f * s, 2.4f * s, -8f * s, sp);
        } else {
            float sw = moving ? (float) Math.sin(ph) * 1.4f * s : 0;
            c.drawLine(-2f * s, -9.4f * s, -3.4f * s + sw * 0.3f, -6.4f * s + sw, sp);
            c.drawLine(2f * s, -9.4f * s, 3.4f * s - sw * 0.3f, -6.4f * s - sw, sp);
        }
        // Kafa
        float hy = -13.4f * s;
        p.setColor(0xFF5A3420);
        c.drawCircle(-4.1f * s, hy + 0.6f * s, 1.7f * s, p); // örgüler (at kuyruğu)
        c.drawCircle(4.1f * s, hy + 0.6f * s, 1.7f * s, p);
        c.drawCircle(-4.6f * s, hy + 2.4f * s, 1.2f * s, p);
        c.drawCircle(4.6f * s, hy + 2.4f * s, 1.2f * s, p);
        p.setColor(0xFFFFD9B8);
        c.drawCircle(0, hy, 3.7f * s, p);
        p.setColor(0xFF5A3420);
        rf.set(-3.9f * s, hy - 4.1f * s, 3.9f * s, hy + 2.6f * s);
        c.drawArc(rf, 180, 180, true, p);
        // Kâkül
        path.reset();
        path.moveTo(-3.8f * s, hy - 0.2f * s);
        path.quadTo(-2.5f * s, hy - 1.5f * s, -1.2f * s, hy - 0.9f * s);
        path.quadTo(0, hy - 2.1f * s, 1.3f * s, hy - 0.9f * s);
        path.quadTo(2.6f * s, hy - 1.6f * s, 3.8f * s, hy - 0.2f * s);
        path.lineTo(3.8f * s, hy - 1f * s);
        path.lineTo(-3.8f * s, hy - 1f * s);
        path.close();
        c.drawPath(path, p);
        // Fiyonklar
        p.setColor(0xFFFF3D8B);
        drawBow(c, -3.6f * s, hy - 1.6f * s, s);
        drawBow(c, 3.6f * s, hy - 1.6f * s, s);
        // Yüz
        float ex = face * 0.35f * s;
        if (happy) {
            sp.setColor(0xFF2B1B12);
            sp.setStrokeWidth(0.45f * s);
            rf.set(-2f * s + ex, hy + 0.1f * s, -0.7f * s + ex, hy + 1.2f * s); c.drawArc(rf, 180, 180, false, sp);
            rf.set(0.7f * s + ex, hy + 0.1f * s, 2f * s + ex, hy + 1.2f * s); c.drawArc(rf, 180, 180, false, sp);
        } else {
            p.setColor(0xFF2B1B12);
            c.drawCircle(-1.35f * s + ex, hy + 0.6f * s, 0.55f * s, p);
            c.drawCircle(1.35f * s + ex, hy + 0.6f * s, 0.55f * s, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(-1.2f * s + ex, hy + 0.4f * s, 0.2f * s, p);
            c.drawCircle(1.5f * s + ex, hy + 0.4f * s, 0.2f * s, p);
        }
        p.setColor(0x66FF6F8F);
        c.drawCircle(-2.3f * s + ex, hy + 1.8f * s, 0.7f * s, p);
        c.drawCircle(2.3f * s + ex, hy + 1.8f * s, 0.7f * s, p);
        sp.setColor(0xFFB23A48);
        sp.setStrokeWidth(0.35f * s);
        rf.set(-0.8f * s + ex, hy + 1.3f * s, 0.8f * s + ex, hy + (happy ? 2.9f : 2.4f) * s);
        c.drawArc(rf, 20, 140, false, sp);
        c.restore();
        drawName(c, "Mihrimah", x, y - 19.5f * s - bob, 0xFFFF6FA8);
    }

    private void drawBow(Canvas c, float x, float y, float s) {
        path.reset();
        path.moveTo(x, y);
        path.lineTo(x - 1.3f * s, y - 0.9f * s);
        path.lineTo(x - 1.3f * s, y + 0.9f * s);
        path.close();
        path.moveTo(x, y);
        path.lineTo(x + 1.3f * s, y - 0.9f * s);
        path.lineTo(x + 1.3f * s, y + 0.9f * s);
        path.close();
        c.drawPath(path, p);
        c.drawCircle(x, y, 0.45f * s, p);
    }

    private void drawBaba(Canvas c, float x, float y, float s, float ph, int face, boolean moving, boolean happy, boolean holding) {
        drawShadow(c, x, y, 4.2f * s, 1.4f * s, 0x40000000);
        float bob = moving ? Math.abs((float) Math.sin(ph)) * 0.8f * s : 0;
        float l1 = moving ? (float) Math.sin(ph) * 1.4f * s : 0;
        c.save();
        c.translate(x, y - bob);
        // Bacaklar (kot)
        p.setColor(0xFF3B5B92);
        c.drawRect(-2.2f * s, -8f * s + Math.max(0, l1) * 0.3f, -0.3f * s, -1f * s + Math.max(0, l1) * 0.2f - Math.max(0, l1), p);
        c.drawRect(0.3f * s, -8f * s + Math.max(0, -l1) * 0.3f, 2.2f * s, -1f * s + Math.max(0, -l1) * 0.2f - Math.max(0, -l1), p);
        p.setColor(0xFF6B3F1F);
        rf.set(-2.8f * s, -1.6f * s - Math.max(0, l1), -0.1f * s, 0.2f * s - Math.max(0, l1)); c.drawOval(rf, p);
        rf.set(0.1f * s, -1.6f * s - Math.max(0, -l1), 2.8f * s, 0.2f * s - Math.max(0, -l1)); c.drawOval(rf, p);
        // Gövde (gömlek)
        p.setColor(0xFF4FA3E0);
        rf.set(-3.2f * s, -16.4f * s, 3.2f * s, -7.4f * s);
        c.drawRoundRect(rf, 1.5f * s, 1.5f * s, p);
        p.setColor(0xFF3C8AC4);
        c.drawRect(-0.25f * s, -15.8f * s, 0.25f * s, -7.6f * s, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(0, -14f * s, 0.25f * s, p);
        c.drawCircle(0, -12f * s, 0.25f * s, p);
        c.drawCircle(0, -10f * s, 0.25f * s, p);
        // Kemer
        p.setColor(0xFF4A2E17);
        c.drawRect(-3.1f * s, -8.2f * s, 3.1f * s, -7.3f * s, p);
        p.setColor(0xFFE0B64A);
        c.drawRect(-0.6f * s, -8.2f * s, 0.6f * s, -7.3f * s, p);
        // Yaka
        p.setColor(0xFF3C8AC4);
        path.reset();
        path.moveTo(-1.8f * s, -16.4f * s); path.lineTo(0, -14.8f * s); path.lineTo(1.8f * s, -16.4f * s); path.close();
        c.drawPath(path, p);
        // Kollar
        sp.setStrokeWidth(1.5f * s);
        if (holding || happy) {
            sp.setColor(0xFF4FA3E0);
            c.drawLine(-3f * s, -15.5f * s, -3.4f * s, -12f * s, sp);
            c.drawLine(3f * s, -15.5f * s, 3.4f * s, -12f * s, sp);
            sp.setColor(0xFFE8B48E);
            c.drawLine(-3.4f * s, -12f * s, -2f * s, -11f * s, sp);
            c.drawLine(3.4f * s, -12f * s, 2f * s, -11f * s, sp);
        } else {
            float sw = moving ? (float) Math.sin(ph) * 1.8f * s : 0;
            sp.setColor(0xFF4FA3E0);
            c.drawLine(-3.1f * s, -15.6f * s, -4.2f * s + sw * 0.3f, -11.5f * s + sw * 0.5f, sp);
            c.drawLine(3.1f * s, -15.6f * s, 4.2f * s - sw * 0.3f, -11.5f * s - sw * 0.5f, sp);
            sp.setColor(0xFFE8B48E);
            c.drawLine(-4.2f * s + sw * 0.3f, -11.5f * s + sw * 0.5f, -4.4f * s + sw * 0.5f, -9.4f * s + sw, sp);
            c.drawLine(4.2f * s - sw * 0.3f, -11.5f * s - sw * 0.5f, 4.4f * s - sw * 0.5f, -9.4f * s - sw, sp);
        }
        // Boyun + kafa
        p.setColor(0xFFE8B48E);
        c.drawRect(-0.9f * s, -17.6f * s, 0.9f * s, -16f * s, p);
        float hy = -20.6f * s;
        c.drawCircle(-3.7f * s, hy + 0.4f * s, 0.8f * s, p); // kulaklar
        c.drawCircle(3.7f * s, hy + 0.4f * s, 0.8f * s, p);
        c.drawCircle(0, hy, 3.7f * s, p);
        // Sakal gölgesi
        p.setColor(0x553A2A20);
        rf.set(-3.2f * s, hy - 0.2f * s, 3.2f * s, hy + 3.7f * s);
        c.drawArc(rf, 0, 180, true, p);
        // Saç
        p.setColor(0xFF2B2B2B);
        rf.set(-3.9f * s, hy - 4.1f * s, 3.9f * s, hy + 1.6f * s);
        c.drawArc(rf, 185, 170, true, p);
        path.reset();
        path.moveTo(-3.6f * s, hy - 1.1f * s);
        path.quadTo(0, hy - 3.4f * s, 3.6f * s, hy - 1.1f * s);
        path.lineTo(3.3f * s, hy - 2.6f * s);
        path.lineTo(-3.3f * s, hy - 2.6f * s);
        path.close();
        c.drawPath(path, p);
        float ex = face * 0.35f * s;
        // Kaşlar
        sp.setColor(0xFF2B2B2B);
        sp.setStrokeWidth(0.45f * s);
        c.drawLine(-2.1f * s + ex, hy - 0.9f * s, -0.7f * s + ex, hy - 1.1f * s, sp);
        c.drawLine(0.7f * s + ex, hy - 1.1f * s, 2.1f * s + ex, hy - 0.9f * s, sp);
        // Gözler
        if (happy) {
            sp.setStrokeWidth(0.4f * s);
            rf.set(-2f * s + ex, hy - 0.2f * s, -0.8f * s + ex, hy + 0.9f * s); c.drawArc(rf, 180, 180, false, sp);
            rf.set(0.8f * s + ex, hy - 0.2f * s, 2f * s + ex, hy + 0.9f * s); c.drawArc(rf, 180, 180, false, sp);
        } else {
            p.setColor(0xFF1E1E1E);
            c.drawCircle(-1.4f * s + ex, hy + 0.2f * s, 0.45f * s, p);
            c.drawCircle(1.4f * s + ex, hy + 0.2f * s, 0.45f * s, p);
        }
        // Burun + bıyık
        p.setColor(0xFFD69C78);
        c.drawCircle(ex, hy + 1.1f * s, 0.55f * s, p);
        p.setColor(0xFF3A2A20);
        path.reset();
        path.moveTo(ex, hy + 1.6f * s);
        path.quadTo(-1.4f * s + ex, hy + 1.3f * s, -2.1f * s + ex, hy + 2.4f * s);
        path.quadTo(-1f * s + ex, hy + 2.1f * s, ex, hy + 2.2f * s);
        path.quadTo(1f * s + ex, hy + 2.1f * s, 2.1f * s + ex, hy + 2.4f * s);
        path.quadTo(1.4f * s + ex, hy + 1.3f * s, ex, hy + 1.6f * s);
        path.close();
        c.drawPath(path, p);
        sp.setColor(0xFF8A3A32);
        sp.setStrokeWidth(0.3f * s);
        rf.set(-0.8f * s + ex, hy + 2f * s, 0.8f * s + ex, hy + (happy ? 3.4f : 3f) * s);
        c.drawArc(rf, 30, 120, false, sp);
        c.restore();
        drawName(c, "Murat Baba", x, y - 26.5f * s - bob, 0xFF4FA3E0);
        if (dashT > 0 && state == PLAYING) {
            sp.setColor(0x88FFFFFF);
            sp.setStrokeWidth(0.5f * s);
            for (int i = 0; i < 3; i++) {
                float yy = y - (6 + i * 5) * s;
                c.drawLine(x - face * 5 * s, yy, x - face * (9 + i) * s, yy, sp);
            }
        }
    }

    private void drawName(Canvas c, String name, float x, float y, int color) {
        tp.setTextSize(2.6f * U);
        sp.setTextSize(2.6f * U);
        sp.setStrokeWidth(0.6f * U);
        sp.setColor(0xCCFFFFFF);
        c.drawText(name, x, y, sp);
        tp.setColor(color);
        c.drawText(name, x, y, tp);
    }

    /** Pamuk: turuncu tekir yavru kedi. lift: zıplarken yukarı kaldırma. */
    private void drawCat(Canvas c, float x, float y, float s, float lift) {
        boolean eating = catState == CAT_EAT;
        boolean held = catState == CAT_CAUGHT;
        boolean moving = state == TITLE || (catState == CAT_RUN && (Math.abs(cvx) + Math.abs(cvy)) > 3 * U);
        if (!held && lift == 0) drawShadow(c, x, y, 4 * s, 1.2f * s, 0x40000000);
        c.save();
        c.translate(x, y - lift);
        if (cFace < 0) c.scale(-1, 1);
        if (held) c.rotate((float) Math.sin(globalTime * 5) * 6);
        int fur = 0xFFF4A340, dark = 0xFFD9822B, light = 0xFFFFF3E3;
        float lp = legPhase;
        // Kuyruk
        sp.setColor(fur);
        sp.setStrokeWidth(1.2f * s);
        path.reset();
        path.moveTo(-3.2f * s, -3.6f * s);
        float tw = (float) Math.sin(tailPhase) * 1.6f * s;
        path.cubicTo(-5.5f * s, -4.5f * s, -5.5f * s + tw, -8f * s, -4f * s + tw, -9.5f * s);
        c.drawPath(path, sp);
        sp.setColor(dark);
        c.drawPoint(-4f * s + tw, -9.5f * s, sp);
        // Bacaklar
        p.setColor(fur);
        float a1 = moving ? (float) Math.sin(lp) * 0.9f * s : 0;
        float a2 = moving ? (float) Math.sin(lp + Math.PI) * 0.9f * s : 0;
        if (held) { a1 = 0.5f * s; a2 = -0.5f * s; }
        c.drawRect(-2.8f * s + a1, -3f * s, -1.8f * s + a1, -0.3f * s, p);
        c.drawRect(-1.3f * s + a2, -3f * s, -0.3f * s + a2, -0.3f * s, p);
        c.drawRect(1f * s + a2, -3f * s, 2f * s + a2, -0.3f * s, p);
        c.drawRect(2.4f * s + a1, -3f * s, 3.4f * s + a1, -0.3f * s, p);
        p.setColor(light);
        c.drawCircle(-2.3f * s + a1, -0.4f * s, 0.6f * s, p);
        c.drawCircle(-0.8f * s + a2, -0.4f * s, 0.6f * s, p);
        c.drawCircle(1.5f * s + a2, -0.4f * s, 0.6f * s, p);
        c.drawCircle(2.9f * s + a1, -0.4f * s, 0.6f * s, p);
        // Gövde
        p.setColor(fur);
        rf.set(-3.8f * s, -6.2f * s, 3.8f * s, -2.2f * s);
        c.drawOval(rf, p);
        p.setColor(light);
        rf.set(-2f * s, -4f * s, 2.6f * s, -2.3f * s);
        c.drawOval(rf, p);
        sp.setColor(dark);
        sp.setStrokeWidth(0.5f * s);
        c.drawLine(-1.8f * s, -6f * s, -1.4f * s, -4.6f * s, sp);
        c.drawLine(-0.4f * s, -6.2f * s, -0.1f * s, -4.8f * s, sp);
        c.drawLine(1f * s, -6.1f * s, 1.2f * s, -4.8f * s, sp);
        // Kafa
        float hx = 3.6f * s, hy = eating ? -3.6f * s : -6f * s;
        p.setColor(fur);
        path.reset();
        path.moveTo(hx - 2.4f * s, hy - 1.2f * s); path.lineTo(hx - 2f * s, hy - 3.9f * s); path.lineTo(hx - 0.4f * s, hy - 2.2f * s); path.close();
        path.moveTo(hx + 2.4f * s, hy - 1.2f * s); path.lineTo(hx + 2f * s, hy - 3.9f * s); path.lineTo(hx + 0.4f * s, hy - 2.2f * s); path.close();
        c.drawPath(path, p);
        p.setColor(0xFFFFB3C6);
        path.reset();
        path.moveTo(hx - 1.9f * s, hy - 1.7f * s); path.lineTo(hx - 1.8f * s, hy - 3.1f * s); path.lineTo(hx - 0.9f * s, hy - 2.2f * s); path.close();
        path.moveTo(hx + 1.9f * s, hy - 1.7f * s); path.lineTo(hx + 1.8f * s, hy - 3.1f * s); path.lineTo(hx + 0.9f * s, hy - 2.2f * s); path.close();
        c.drawPath(path, p);
        p.setColor(fur);
        c.drawCircle(hx, hy, 2.6f * s, p);
        p.setColor(light);
        rf.set(hx - 1.6f * s, hy + 0.1f * s, hx + 1.6f * s, hy + 2.3f * s);
        c.drawOval(rf, p);
        sp.setColor(dark);
        sp.setStrokeWidth(0.4f * s);
        c.drawLine(hx, hy - 2.5f * s, hx, hy - 1.4f * s, sp);
        c.drawLine(hx - 0.8f * s, hy - 2.3f * s, hx - 0.6f * s, hy - 1.5f * s, sp);
        c.drawLine(hx + 0.8f * s, hy - 2.3f * s, hx + 0.6f * s, hy - 1.5f * s, sp);
        // Gözler
        if (eating || held) {
            sp.setColor(0xFF3A2A20);
            sp.setStrokeWidth(0.3f * s);
            rf.set(hx - 1.6f * s, hy - 0.8f * s, hx - 0.4f * s, hy + 0.2f * s); c.drawArc(rf, 0, 180, false, sp);
            rf.set(hx + 0.4f * s, hy - 0.8f * s, hx + 1.6f * s, hy + 0.2f * s); c.drawArc(rf, 0, 180, false, sp);
        } else {
            p.setColor(0xFF5BC26A);
            c.drawCircle(hx - 1f * s, hy - 0.3f * s, 0.65f * s, p);
            c.drawCircle(hx + 1f * s, hy - 0.3f * s, 0.65f * s, p);
            p.setColor(0xFF1E1E1E);
            rf.set(hx - 1.2f * s, hy - 0.85f * s, hx - 0.8f * s, hy + 0.25f * s); c.drawOval(rf, p);
            rf.set(hx + 0.8f * s, hy - 0.85f * s, hx + 1.2f * s, hy + 0.25f * s); c.drawOval(rf, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(hx - 0.85f * s, hy - 0.6f * s, 0.18f * s, p);
            c.drawCircle(hx + 1.15f * s, hy - 0.6f * s, 0.18f * s, p);
        }
        p.setColor(0xFFFF7FA0);
        path.reset();
        path.moveTo(hx - 0.4f * s, hy + 0.6f * s); path.lineTo(hx + 0.4f * s, hy + 0.6f * s); path.lineTo(hx, hy + 1.1f * s); path.close();
        c.drawPath(path, p);
        sp.setColor(0xAA3A2A20);
        sp.setStrokeWidth(0.15f * s);
        c.drawLine(hx - 0.8f * s, hy + 1.1f * s, hx - 3.2f * s, hy + 0.6f * s, sp);
        c.drawLine(hx - 0.8f * s, hy + 1.3f * s, hx - 3.2f * s, hy + 1.6f * s, sp);
        c.drawLine(hx + 0.8f * s, hy + 1.1f * s, hx + 3.2f * s, hy + 0.6f * s, sp);
        c.drawLine(hx + 0.8f * s, hy + 1.3f * s, hx + 3.2f * s, hy + 1.6f * s, sp);
        // Pembe tasma + çan
        p.setColor(0xFFFF3D8B);
        rf.set(hx - 1.8f * s, hy + 1.9f * s, hx + 1.8f * s, hy + 2.6f * s);
        c.drawRoundRect(rf, 0.3f * s, 0.3f * s, p);
        p.setColor(0xFFFFD23C);
        c.drawCircle(hx, hy + 2.9f * s, 0.5f * s, p);
        c.restore();
        if (state == PLAYING && stamina < 0.2f && catState == CAT_RUN) {
            tp.setTextSize(3f * U);
            tp.setColor(0xFF6FA8FF);
            c.drawText("💦", x - cFace * 3 * U, y - 9 * U - lift, tp);
        }
        drawName(c, "Pamuk", x, y - 11f * s - lift, 0xFFE0822B);
    }

    private void drawObstacle(Canvas c, float[] o, int idx) {
        float x = o[0], y = o[1], r = o[2];
        int type = (int) o[3];
        int th = theme();
        switch (type) {
            case OB_BUSH: {
                drawShadow(c, x, y + 0.4f * r, r * 1.1f, r * 0.45f, 0x33000000);
                int dk = th == 2 ? 0xFF1F4A3A : 0xFF3E9B4F, md = th == 2 ? 0xFF2A5E49 : 0xFF4DB35E, lt = th == 2 ? 0xFF35705A : 0xFF67C876;
                p.setColor(dk);
                c.drawCircle(x - r * 0.5f, y - r * 0.2f, r * 0.65f, p);
                c.drawCircle(x + r * 0.5f, y - r * 0.2f, r * 0.65f, p);
                p.setColor(md);
                c.drawCircle(x, y - r * 0.6f, r * 0.75f, p);
                p.setColor(lt);
                c.drawCircle(x - r * 0.25f, y - r * 0.85f, r * 0.3f, p);
                p.setColor(o[4] > 0.5f ? 0xFFFF5C8A : 0xFFFFFFFF);
                c.drawCircle(x - r * 0.4f, y - r * 0.2f, r * 0.1f, p);
                c.drawCircle(x + r * 0.35f, y - r * 0.6f, r * 0.1f, p);
                c.drawCircle(x + r * 0.6f, y - r * 0.05f, r * 0.1f, p);
                break;
            }
            case OB_TREE: {
                drawShadow(c, x, y, r * 3.2f, r * 1.1f, 0x33000000);
                p.setColor(0xFF8B5A2B);
                rf.set(x - r * 0.55f, y - r * 3.4f, x + r * 0.55f, y + r * 0.2f);
                c.drawRoundRect(rf, r * 0.2f, r * 0.2f, p);
                int dk = th == 2 ? 0xDD1E4636 : 0xEE3B8C45, lt = th == 2 ? 0xDD2C5E4A : 0xEE56B25F;
                p.setColor(dk);
                c.drawCircle(x - r * 1.5f, y - r * 4f, r * 1.8f, p);
                c.drawCircle(x + r * 1.5f, y - r * 4f, r * 1.8f, p);
                c.drawCircle(x, y - r * 5.4f, r * 2.2f, p);
                p.setColor(lt);
                c.drawCircle(x - r * 0.6f, y - r * 5.8f, r * 0.9f, p);
                if (th != 2) {
                    p.setColor(0xFFE84545);
                    c.drawCircle(x + r * 1.2f, y - r * 4.6f, r * 0.35f, p);
                    c.drawCircle(x - r * 1.4f, y - r * 3.6f, r * 0.35f, p);
                    c.drawCircle(x + r * 0.2f, y - r * 6.2f, r * 0.35f, p);
                }
                break;
            }
            case OB_POT: {
                drawShadow(c, x, y, r * 1.1f, r * 0.4f, 0x33000000);
                p.setColor(0xFFC96A3B);
                path.reset();
                path.moveTo(x - r, y - r * 1.6f); path.lineTo(x + r, y - r * 1.6f); path.lineTo(x + r * 0.7f, y); path.lineTo(x - r * 0.7f, y); path.close();
                c.drawPath(path, p);
                p.setColor(0xFFB25A2E);
                c.drawRect(x - r * 1.1f, y - r * 1.9f, x + r * 1.1f, y - r * 1.5f, p);
                int[] fc = {0xFFFF5C8A, 0xFFFFE066, 0xFFB38BFF};
                for (int k = 0; k < 3; k++) {
                    float fx = x + (k - 1) * r * 0.6f, fy = y - r * (2.6f + (k % 2) * 0.5f);
                    sp.setColor(0xFF3E9B4F);
                    sp.setStrokeWidth(0.3f * U);
                    c.drawLine(fx, y - r * 1.8f, fx, fy, sp);
                    p.setColor(fc[k]);
                    c.drawCircle(fx, fy, r * 0.35f, p);
                    p.setColor(0xFFFFF3A0);
                    c.drawCircle(fx, fy, r * 0.13f, p);
                }
                break;
            }
            case OB_BOX: {
                boolean hiding = catState == CAT_HIDE && hideBox == idx;
                drawShadow(c, x, y, r * 1.1f, r * 0.35f, 0x33000000);
                c.save();
                if (hiding) c.rotate((float) Math.sin(globalTime * 25) * 4, x, y);
                p.setColor(0xFFC8955C);
                c.drawRect(x - r, y - r * 1.3f, x + r, y, p);
                p.setColor(0xFFB07D48);
                c.drawRect(x - r, y - r * 1.3f, x + r, y - r * 1.05f, p);
                p.setColor(0xFFDDB07A);
                path.reset();
                path.moveTo(x - r, y - r * 1.3f); path.lineTo(x - r * 1.35f, y - r * 1.75f); path.lineTo(x - r * 0.2f, y - r * 1.6f); path.lineTo(x - r * 0.05f, y - r * 1.3f); path.close();
                path.moveTo(x + r, y - r * 1.3f); path.lineTo(x + r * 1.35f, y - r * 1.75f); path.lineTo(x + r * 0.2f, y - r * 1.6f); path.lineTo(x + r * 0.05f, y - r * 1.3f); path.close();
                c.drawPath(path, p);
                sp.setColor(0xFF8A6034);
                sp.setStrokeWidth(0.25f * U);
                c.drawLine(x - r * 0.5f, y - r * 0.6f, x + r * 0.5f, y - r * 0.6f, sp);
                if (hiding) {
                    p.setColor(0xFFF4A340);
                    path.reset();
                    path.moveTo(x - r * 0.55f, y - r * 1.3f); path.lineTo(x - r * 0.4f, y - r * 1.75f); path.lineTo(x - r * 0.15f, y - r * 1.3f); path.close();
                    path.moveTo(x + r * 0.55f, y - r * 1.3f); path.lineTo(x + r * 0.4f, y - r * 1.75f); path.lineTo(x + r * 0.15f, y - r * 1.3f); path.close();
                    c.drawPath(path, p);
                }
                c.restore();
                if (hiding) {
                    tp.setTextSize(3.5f * U);
                    tp.setColor(0xFFFFFFFF);
                    c.drawText("?", x + r * 1.3f, y - r * 2f + (float) Math.sin(globalTime * 6) * U, tp);
                }
                break;
            }
            default:
                break;
        }
    }

    private void drawFish(Canvas c, float x, float y, float s, float rot, boolean blink) {
        if (blink) return;
        c.save();
        c.translate(x, y - 1.5f * s);
        c.rotate(rot * 57f);
        p.setColor(0xFF7FD4FF);
        rf.set(-2.2f * s, -1.2f * s, 1.8f * s, 1.2f * s);
        c.drawOval(rf, p);
        path.reset();
        path.moveTo(-1.8f * s, 0); path.lineTo(-3.4f * s, -1.3f * s); path.lineTo(-3.4f * s, 1.3f * s); path.close();
        c.drawPath(path, p);
        p.setColor(0xFF4FA8D8);
        c.drawCircle(-0.3f * s, 0.2f * s, 0.35f * s, p);
        c.drawCircle(0.5f * s, -0.3f * s, 0.3f * s, p);
        p.setColor(0xFF1E1E1E);
        c.drawCircle(1f * s, -0.3f * s, 0.25f * s, p);
        c.restore();
    }

    private void drawPowerup(Canvas c) {
        if (puLife < 2 && ((int) (globalTime * 8)) % 2 == 0) return;
        float bob = (float) Math.sin(globalTime * 4) * U;
        p.setShader(new RadialGradient(puX, puY - 3 * U + bob, 5 * U, 0x88FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(puX, puY - 3 * U + bob, 5 * U, p);
        p.setShader(null);
        if (puType == 0) drawStar(c, puX, puY - 3 * U + bob, 2.8f * U, 0xFFFFD23C, globalTime);
        else drawFish(c, puX, puY - 1.5f * U + bob, U * 1.3f, 0, false);
    }

    private void drawStar(Canvas c, float x, float y, float r, int color, float rot) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            double a = rot + i * Math.PI / 5 - Math.PI / 2;
            float rr = (i % 2 == 0) ? r : r * 0.45f;
            float px = x + (float) Math.cos(a) * rr, py = y + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        p.setColor(color);
        c.drawPath(path, p);
    }

    private void drawHeart(Canvas c, float x, float y, float s, int color) {
        path.reset();
        path.moveTo(x, y + s * 0.9f);
        path.cubicTo(x - s * 1.4f, y - s * 0.1f, x - s * 0.7f, y - s * 1.1f, x, y - s * 0.35f);
        path.cubicTo(x + s * 0.7f, y - s * 1.1f, x + s * 1.4f, y - s * 0.1f, x, y + s * 0.9f);
        path.close();
        p.setColor(color);
        c.drawPath(path, p);
    }

    private void drawButterfly(Canvas c, float x, float y, float ph, int color) {
        float flap = Math.abs((float) Math.sin(ph)) * 1.4f * U + 0.3f * U;
        float yy = y - 9 * U;
        p.setColor(color);
        rf.set(x - flap - 0.2f * U, yy - 1.2f * U, x - 0.2f * U, yy + 0.4f * U); c.drawOval(rf, p);
        rf.set(x + 0.2f * U, yy - 1.2f * U, x + flap + 0.2f * U, yy + 0.4f * U); c.drawOval(rf, p);
        p.setColor(0xFF3A2A20);
        rf.set(x - 0.2f * U, yy - 0.9f * U, x + 0.2f * U, yy + 0.9f * U); c.drawOval(rf, p);
    }

    private void drawParticles(Canvas c) {
        for (P q : parts) {
            float a = Math.min(1, q.life / q.max * 1.5f);
            int col = (q.color & 0x00FFFFFF) | ((int) (((q.color >>> 24) & 0xFF) * a) << 24);
            if (q.type == 0) drawHeart(c, q.x, q.y, q.size, col);
            else if (q.type == 1) drawStar(c, q.x, q.y, q.size, col, q.life * 3);
            else { p.setColor(col); c.drawCircle(q.x, q.y, q.size, p); }
        }
        for (FT f : texts) {
            float a = Math.min(1, f.life * 2);
            drawOutlined(c, f.s, f.x, f.y, 4.5f * U, (f.color & 0x00FFFFFF) | ((int) (255 * a) << 24), Color.argb((int) (200 * a), 60, 30, 40));
        }
    }

    private void drawBubbles(Canvas c) {
        for (int i = 0; i < 3; i++) {
            if (bubble[i] == null || bubbleT[i] <= 0) continue;
            if (i == SPK_CAT && catState == CAT_HIDE && !"...".equals(bubble[i])) continue;
            float x, y;
            if (i == SPK_MIH) { x = mx; y = my - 23 * U; }
            else if (i == SPK_BABA) { x = bx; y = by - 30 * U; }
            else {
                x = cx;
                y = cy - 15 * U;
                if (catState == CAT_HIDE && hideBox >= 0) { float[] o = obstacles.get(hideBox); x = o[0]; y = o[1] - 12 * U; }
            }
            float a = Math.min(1, bubbleT[i] * 3);
            tp.setTextSize(3.2f * U);
            float tw = tp.measureText(bubble[i]);
            float pad = 1.6f * U;
            float bw = tw + pad * 2, bh = 5.2f * U;
            float bxx = clamp(x - bw / 2, 1 * U, W - bw - 1 * U);
            float byy = Math.max(hudH + U, y - bh);
            p.setColor(Color.argb((int) (235 * a), 255, 255, 255));
            rf.set(bxx, byy, bxx + bw, byy + bh);
            c.drawRoundRect(rf, 2 * U, 2 * U, p);
            path.reset();
            float tipX = clamp(x, bxx + 2 * U, bxx + bw - 2 * U);
            path.moveTo(tipX - 1.2f * U, byy + bh - 0.1f * U);
            path.lineTo(tipX + 1.2f * U, byy + bh - 0.1f * U);
            path.lineTo(tipX, byy + bh + 1.6f * U);
            path.close();
            c.drawPath(path, p);
            int col = i == SPK_MIH ? 0xFFD6336C : (i == SPK_BABA ? 0xFF1C6FB0 : 0xFFC0661A);
            tp.setColor((col & 0x00FFFFFF) | ((int) (255 * a) << 24));
            c.drawText(bubble[i], bxx + bw / 2, byy + bh / 2 + 1.1f * U, tp);
        }
    }

    private void drawHud(Canvas c) {
        p.setColor(0xCC2B2140);
        c.drawRect(0, 0, W, hudH, p);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTextSize(4.2f * U);
        tp.setColor(0xFFFFFFFF);
        c.drawText("Seviye " + level + " · " + THEME_NAMES[theme()], 3 * U, hudH * 0.66f, tp);
        tp.setTextAlign(Paint.Align.CENTER);
        // Yakalanan kediler
        float iconX = W * 0.5f - (target - 1) * 3.4f * U;
        for (int i = 0; i < target; i++) {
            drawCatHead(c, iconX + i * 6.8f * U, hudH / 2, 2.3f * U, i < catches);
        }
        // Süre
        boolean low = timeLeft <= 10;
        float pulse = low ? 1 + 0.12f * (float) Math.abs(Math.sin(globalTime * 6)) : 1;
        tp.setTextSize(4.6f * U * pulse);
        tp.setColor(low ? 0xFFFF6B6B : 0xFFFFFFFF);
        c.drawText("⏱ " + (int) Math.ceil(timeLeft), W - 38 * U, hudH * 0.68f, tp);
        tp.setTextSize(4.2f * U);
        tp.setColor(0xFFFFE14D);
        c.drawText("★ " + score, W - 20 * U, hudH * 0.66f, tp);
        // Duraklat butonu
        p.setColor(0x55FFFFFF);
        c.drawCircle(pauseBx, pauseBy, 4 * U, p);
        p.setColor(0xFFFFFFFF);
        c.drawRect(pauseBx - 1.5f * U, pauseBy - 1.8f * U, pauseBx - 0.5f * U, pauseBy + 1.8f * U, p);
        c.drawRect(pauseBx + 0.5f * U, pauseBy - 1.8f * U, pauseBx + 1.5f * U, pauseBy + 1.8f * U, p);
        // Kedinin enerji çubuğu
        float ew = 18 * U, ex = 3 * U, ey = hudH + 1.2f * U;
        p.setColor(0x88000000);
        rf.set(ex, ey, ex + ew, ey + 2 * U);
        c.drawRoundRect(rf, U, U, p);
        p.setColor(stamina > 0.3f ? 0xFFF4A340 : 0xFF6FA8FF);
        rf.set(ex, ey, ex + ew * stamina, ey + 2 * U);
        c.drawRoundRect(rf, U, U, p);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTextSize(2.4f * U);
        tp.setColor(0xFFFFFFFF);
        c.drawText("Pamuk'un enerjisi", ex + ew + U, ey + 1.8f * U, tp);
        tp.setTextAlign(Paint.Align.CENTER);
    }

    private void drawCatHead(Canvas c, float x, float y, float r, boolean filled) {
        int col = filled ? 0xFFF4A340 : 0x55FFFFFF;
        p.setColor(col);
        path.reset();
        path.moveTo(x - r * 0.95f, y - r * 0.2f); path.lineTo(x - r * 0.8f, y - r * 1.35f); path.lineTo(x - r * 0.1f, y - r * 0.8f); path.close();
        path.moveTo(x + r * 0.95f, y - r * 0.2f); path.lineTo(x + r * 0.8f, y - r * 1.35f); path.lineTo(x + r * 0.1f, y - r * 0.8f); path.close();
        c.drawPath(path, p);
        c.drawCircle(x, y, r, p);
        if (filled) {
            p.setColor(0xFF2B1B12);
            c.drawCircle(x - r * 0.38f, y - r * 0.1f, r * 0.14f, p);
            c.drawCircle(x + r * 0.38f, y - r * 0.1f, r * 0.14f, p);
            p.setColor(0xFFFF7FA0);
            c.drawCircle(x, y + r * 0.25f, r * 0.12f, p);
        }
    }

    private void drawButtons(Canvas c) {
        if (state != PLAYING) return;
        // Balık butonu
        p.setColor(fishCount > 0 && !fishOn ? 0xDD1C6FB0 : 0x88555555);
        c.drawCircle(fishBx, fishBy, btnR, p);
        sp.setColor(0xFFFFFFFF);
        sp.setStrokeWidth(0.6f * U);
        c.drawCircle(fishBx, fishBy, btnR, sp);
        drawFish(c, fishBx + 0.5f * U, fishBy + 0.5f * U, U * 1.4f, 0, false);
        tp.setTextSize(3.2f * U);
        tp.setColor(0xFFFFFFFF);
        c.drawText("x" + fishCount, fishBx, fishBy + 5.8f * U, tp);
        tp.setTextSize(2.6f * U);
        drawOutlined(c, "Balık at", fishBx, fishBy - btnR - 1.2f * U, 2.8f * U, 0xFFFFFFFF, 0xAA000000);

        // Baba koş butonu
        boolean ready = dashCd <= 0;
        p.setColor(ready ? 0xDDFF8A3D : 0x88555555);
        c.drawCircle(dashBx, dashBy, btnR, p);
        c.drawCircle(dashBx, dashBy, btnR, sp);
        if (!ready) {
            sp.setColor(0xFFFFE14D);
            sp.setStrokeWidth(1f * U);
            rf.set(dashBx - btnR + U, dashBy - btnR + U, dashBx + btnR - U, dashBy + btnR - U);
            c.drawArc(rf, -90, 360 * (1 - dashCd / 6f), false, sp);
        }
        tp.setTextSize(5.5f * U);
        tp.setColor(0xFFFFFFFF);
        c.drawText("»", dashBx, dashBy + 1.8f * U, tp);
        drawOutlined(c, "Baba koş!", dashBx, dashBy - btnR - 1.2f * U, 2.8f * U, 0xFFFFFFFF, 0xAA000000);
    }

    private void drawHint(Canvas c) {
        float a = 0.5f + 0.5f * (float) Math.sin(globalTime * 4);
        float hx = W * 0.5f, hy = H * 0.72f;
        p.setColor(Color.argb((int) (120 + 100 * a), 255, 255, 255));
        c.drawCircle(hx, hy, (2.5f + a * 1.5f) * U, p);
        drawOutlined(c, "Ekrana dokun ve parmağını gezdir: Mihrimah oraya koşar!", W / 2f, H * 0.62f, 3.8f * U, 0xFFFFFFFF, 0xCC2B2140);
    }

    private void drawOutlined(Canvas c, String s, float x, float y, float size, int fill, int stroke) {
        sp.setTextSize(size);
        sp.setStrokeWidth(size * 0.22f);
        sp.setColor(stroke);
        c.drawText(s, x, y, sp);
        tp.setTextSize(size);
        tp.setColor(fill);
        c.drawText(s, x, y, tp);
    }

    private void drawPanel(Canvas c, float w, float h) {
        p.setColor(0x99000000);
        c.drawRect(0, 0, W, H, p);
        float x = (W - w) / 2, y = (H - h) / 2;
        p.setColor(0xFFFFF7EC);
        rf.set(x, y, x + w, y + h);
        c.drawRoundRect(rf, 5 * U, 5 * U, p);
        sp.setColor(0xFFFF8FB8);
        sp.setStrokeWidth(1.2f * U);
        c.drawRoundRect(rf, 5 * U, 5 * U, sp);
    }

    private void drawButton(Canvas c, RectF r, String label, int color) {
        p.setColor(0x33000000);
        rf.set(r.left, r.top + U, r.right, r.bottom + U);
        c.drawRoundRect(rf, 4 * U, 4 * U, p);
        p.setColor(color);
        c.drawRoundRect(r, 4 * U, 4 * U, p);
        tp.setTextSize(r.height() * 0.45f);
        tp.setColor(0xFFFFFFFF);
        c.drawText(label, r.centerX(), r.centerY() + r.height() * 0.16f, tp);
    }

    private void drawPause(Canvas c) {
        drawPanel(c, 70 * U, 50 * U);
        drawOutlined(c, "Mola!", W / 2f, H / 2f - 12 * U, 8 * U, 0xFFFF6FA8, 0xFFFFFFFF);
        tp.setTextSize(3.8f * U);
        tp.setColor(0xFF5A4A6A);
        c.drawText("Devam etmek için ekrana dokun", W / 2f, H / 2f, tp);
        menuBtn.set(W / 2f - 18 * U, H / 2f + 6 * U, W / 2f + 18 * U, H / 2f + 16 * U);
        drawButton(c, menuBtn, "Ana Menü", 0xFF8C7AE6);
    }

    private void drawLevelDone(Canvas c) {
        drawPanel(c, 90 * U, 70 * U);
        float cy0 = H / 2f - 22 * U;
        drawOutlined(c, "Harika! Seviye " + level + " tamam!", W / 2f, cy0, 6.5f * U, 0xFFFF6FA8, 0xFFFFFFFF);
        tp.setTextSize(4 * U);
        tp.setColor(0xFF5A4A6A);
        c.drawText("Pamuk " + catches + " kez yakalandı", W / 2f, cy0 + 10 * U, tp);
        c.drawText("Süre bonusu: +" + (int) levelBonus, W / 2f, cy0 + 16 * U, tp);
        tp.setColor(0xFFE0822B);
        tp.setTextSize(5 * U);
        c.drawText("Puan: " + score, W / 2f, cy0 + 24 * U, tp);
        // Mutlu aile
        drawMihrimah(c, W / 2f - 12 * U, cy0 + 45 * U, U * 0.55f, 0, 1, false, true, true);
        drawBaba(c, W / 2f + 12 * U, cy0 + 45 * U, U * 0.55f, 0, -1, false, true, false);
        drawHeart(c, W / 2f, cy0 + 32 * U + (float) Math.sin(globalTime * 5) * U, 2.5f * U, 0xFFFF5C8A);
        if (stateTime > 1f) {
            tp.setTextSize(3.6f * U);
            tp.setColor(0xFF8C7AE6);
            c.drawText("Sıradaki: " + THEME_NAMES[level % 3] + " — devam için dokun", W / 2f, cy0 + 50 * U, tp);
        }
    }

    private void drawGameOver(Canvas c) {
        drawPanel(c, 90 * U, 72 * U);
        float cy0 = H / 2f - 24 * U;
        drawOutlined(c, "Süre bitti!", W / 2f, cy0, 7 * U, 0xFFFF6B6B, 0xFFFFFFFF);
        tp.setTextSize(4 * U);
        tp.setColor(0xFF5A4A6A);
        c.drawText("Pamuk bu sefer kaçtı... Ama çok yaklaştınız!", W / 2f, cy0 + 9 * U, tp);
        tp.setTextSize(5 * U);
        tp.setColor(0xFFE0822B);
        c.drawText("Puan: " + score + "    En iyi: " + best, W / 2f, cy0 + 18 * U, tp);
        tp.setTextSize(3.6f * U);
        tp.setColor(0xFF5A4A6A);
        c.drawText("Ulaşılan seviye: " + level + " (" + THEME_NAMES[theme()] + ")", W / 2f, cy0 + 25 * U, tp);
        againBtn.set(W / 2f - 38 * U, cy0 + 32 * U, W / 2f - 2 * U, cy0 + 43 * U);
        menuBtn.set(W / 2f + 2 * U, cy0 + 32 * U, W / 2f + 38 * U, cy0 + 43 * U);
        if (stateTime > 0.8f) {
            drawButton(c, againBtn, "Tekrar Oyna", 0xFFFF6FA8);
            drawButton(c, menuBtn, "Ana Menü", 0xFF8C7AE6);
        }
    }

    private void drawTitle(Canvas c) {
        ensureBg(0);
        c.drawBitmap(bg, 0, 0, null);
        // Gökyüzü şeridi
        p.setShader(new LinearGradient(0, 0, 0, H * 0.45f, 0xFF8FD3FF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H * 0.45f, p);
        p.setShader(null);
        // Güneş
        p.setColor(0xFFFFE066);
        c.drawCircle(W - 14 * U, 14 * U, 8 * U, p);
        // Bulutlar
        p.setColor(0xEEFFFFFF);
        for (int i = 0; i < 3; i++) {
            float x = ((globalTime * 4 * U + i * W / 3f) % (W + 30 * U)) - 15 * U;
            float y = (8 + i * 6) * U;
            c.drawCircle(x, y, 4 * U, p);
            c.drawCircle(x + 5 * U, y - 2 * U, 5 * U, p);
            c.drawCircle(x + 10 * U, y, 4 * U, p);
        }

        // Kovalamaca canlandırması (y sırasına göre)
        boolean catFront = cy > my;
        if (!catFront) drawCat(c, cx, cy, U * 0.8f, 0);
        if (by < my) {
            drawBaba(c, bx, by, U * 0.8f, bPhase, bFace, true, false, false);
            drawMihrimah(c, mx, my, U * 0.8f, mPhase, mFace, true, false, false);
        } else {
            drawMihrimah(c, mx, my, U * 0.8f, mPhase, mFace, true, false, false);
            drawBaba(c, bx, by, U * 0.8f, bPhase, bFace, true, false, false);
        }
        if (catFront) drawCat(c, cx, cy, U * 0.8f, 0);
        if (bubble[SPK_CAT] != null && bubbleT[SPK_CAT] > 0) drawBubbles(c);
        drawParticles(c);

        float wob = (float) Math.sin(globalTime * 2) * 1.5f;
        c.save();
        c.rotate(wob * 0.5f, W / 2f, 15 * U);
        drawOutlined(c, "Pamuk'u Yakala!", W / 2f, 18 * U, 11 * U, 0xFFFF6FA8, 0xFFFFFFFF);
        c.restore();
        drawOutlined(c, "Mihrimah ile Murat Baba, kaçan yavru kediyi arıyor", W / 2f, 26 * U, 4 * U, 0xFF5A3A7A, 0xEEFFFFFF);

        playBtn.set(W / 2f - 20 * U, 32 * U, W / 2f + 20 * U, 45 * U);
        float pulse = 1 + 0.04f * (float) Math.sin(globalTime * 5);
        c.save();
        c.scale(pulse, pulse, playBtn.centerX(), playBtn.centerY());
        drawButton(c, playBtn, "OYNA", 0xFFFF6FA8);
        c.restore();

        soundBtn.set(4 * U, 4 * U, 30 * U, 12 * U);
        drawButton(c, soundBtn, snd.enabled ? "Ses: Açık" : "Ses: Kapalı", 0xFF8C7AE6);

        tp.setTextAlign(Paint.Align.LEFT);
        float ly = 51 * U;
        String[] lines = {
                "• Parmağınla Mihrimah'ı yönet, Murat Baba öbür taraftan sıkıştırır.",
                "• Balık at: Pamuk yemeğe koşar ve durur. • Baba koş!: Murat hızlanır.",
                "• Yıldız = hız, kutular = saklanma yeri! Pamuk yorulunca yavaşlar."
        };
        for (int i = 0; i < lines.length; i++) {
            tp.setTextSize(3.3f * U);
            sp.setTextAlign(Paint.Align.LEFT);
            sp.setTextSize(3.3f * U);
            sp.setStrokeWidth(0.8f * U);
            sp.setColor(0xDDFFFFFF);
            c.drawText(lines[i], 6 * U, ly + i * 5 * U, sp);
            tp.setColor(0xFF3A2A50);
            c.drawText(lines[i], 6 * U, ly + i * 5 * U, tp);
        }
        sp.setTextAlign(Paint.Align.CENTER);
        tp.setTextAlign(Paint.Align.CENTER);
        if (best > 0) drawOutlined(c, "En iyi: " + best, playBtn.right + 16 * U, playBtn.centerY() + 1.5f * U, 4 * U, 0xFFFFE14D, 0xCC2B2140);
    }
}
