import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.util.ArrayList;
import java.util.List;

public class AsciiDoom {

    // =========================================================
    // Fixed-point 16.16 Arithmetic
    // =========================================================
    static final int FIX_SHIFT = 16;
    static final int FIX_ONE   = 1 << FIX_SHIFT;
    static final int FIX_HALF  = FIX_ONE >>> 1;

    static int fixFromInt(int v)    { return v << FIX_SHIFT; }
    static int fixToInt(int v)      { return v >> FIX_SHIFT; }
    static int fixMul(int a, int b) { return (int)(((long)a * b) >> FIX_SHIFT); }
    static int fixDiv(int a, int b) { return (int)(((long)a << FIX_SHIFT) / b); }

    static final long DELTA_CAP = 1L << 40;
    static long invDistFixed(int v) {
        if (v == 0) return DELTA_CAP;
        long av = v < 0 ? -(long)v : (long)v;
        long r = ((long)FIX_ONE * (long)FIX_ONE) / av;
        if (r > DELTA_CAP || r < 0) r = DELTA_CAP;
        return r;
    }

    // =========================================================
    // Terminal State
    // =========================================================
    static Terminal terminal;
    static NonBlockingReader reader;
    static Attributes originalAttrs;

    // =========================================================
    // Structured Map - Strict Chokepoint at Door (Row 10)
    // =========================================================
    static final String[] RAW_MAP = {
        "║ S · · · █ · · · · · · · · · · · · · █ · · · · · ║",
        "║ █ █ █ · █ · █ █ █ █ █ █ █ █ █ █ █ · █ · █ █ █ · ║",
        "║ · · · · █ · · · · · · · · · · · █ · █ · · · █ · ║",
        "║ · █ █ █ █ █ █ █ █ █ █ █ █ █ █ · █ · █ █ █ · █ · ║",
        "║ · · · · · · · · · · · · · · █ · █ · · · █ · █ · ║",
        "║ █ █ █ █ █ █ █ █ █ █ █ █ █ · █ · █ █ █ · █ · █ · ║",
        "║ · · · · · · · · · · · · █ · █ · · · · · █ · █ · ║",
        "║ · █ █ █ █ █ █ █ █ █ █ · █ · █ █ █ █ █ █ █ · █ · ║",
        "║ · · · · · · · · · · █ · · · · · · · ◆ · · · █ · ║",
        "║ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ ╬ █ █ █ █ █ █ █ █ ║", // Solid division (Only Door crosses)
        "║ · · · · · · · · · · · · · · · · · · · · · · · ║",
        "║ · █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ · ║",
        "║ · █ · · · · · · · · · · · · · · · · · · · █ · ║",
        "║ · █ · █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ · █ · ║",
        "║ · █ · █ · · · · · · · ? · · · · · · · █ · █ · ║",
        "║ · █ · █ · █ █ █ █ █ █ █ █ █ █ █ █ █ · █ · █ · ║",
        "║ · █ · █ · █ · · · · · ★ · · · · · █ · █ · █ · ║",
        "║ · █ · █ · █ · █ █ █ █ █ █ █ █ █ · █ · █ · █ · ║",
        "║ · · · █ · · · · · · · · · · · · · █ · · · · · ║",
        "║ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ ║",
        "║ · · · · · · · · · · · · · · · · · · · · · · E ║"
    };

    static char[][] grid;
    static int mapW, mapH;
    static int spawnX, spawnY;

    // =========================================================
    // Items
    // =========================================================
    static class Item {
        int x, y;
        char glyph;
        char kind;    // 'K' key, 'T' treasure, 'M' mystery/relic
        boolean collected;
        Item(int x, int y, char g, char k) {
            this.x = x; this.y = y; this.glyph = g; this.kind = k;
        }
    }
    static final List<Item> items = new ArrayList<>();

    // =========================================================
    // Game State
    // =========================================================
    static boolean running = true;
    static boolean hasKey, hasTreasure, hasRelic;
    static boolean won = false;
    static String message = "";
    static long messageUntil = 0;

    // =========================================================
    // Player State
    // =========================================================
    static int playerX, playerY;
    static int dirX   = FIX_ONE, dirY   = 0;
    static int planeX = 0,       planeY = (int)(FIX_ONE * 0.66);

    static final int MOVE_SPEED = FIX_ONE / 8;
    static final int ROT_COS = 65281;
    static final int ROT_SIN = 5712;

    // Input Flags
    static boolean keyW, keyA, keyS, keyD, keyTurnL, keyTurnR;

    // Screen Buffer
    static char[][] screen;
    static int screenW = 80, screenH = 24, viewH = 22;
    static long[] zbuf;

    // =========================================================
    // Map Parsing
    // =========================================================
    static void parseMap() {
        int maxLen = 0;
        for (String s : RAW_MAP) {
            String content = stripBorders(s);
            if (content.length() > maxLen) maxLen = content.length();
        }
        if ((maxLen & 1) == 1) maxLen++;

        mapH = RAW_MAP.length;
        mapW = maxLen / 2;
        grid = new char[mapH][mapW];

        for (int y = 0; y < mapH; y++) {
            String content = stripBorders(RAW_MAP[y]);
            StringBuilder sb = new StringBuilder(content);
            while (sb.length() < maxLen) sb.append(' ');

            for (int x = 0; x < mapW; x++) {
                char c = sb.charAt(x * 2);
                switch (c) {
                    case '█': grid[y][x] = '#'; break;
                    case '╬': grid[y][x] = 'D'; break;
                    case 'S': grid[y][x] = '.'; spawnX = x; spawnY = y; break;
                    case 'E': grid[y][x] = 'X'; break;
                    case '◆': grid[y][x] = '.'; items.add(new Item(x, y, '◆', 'K')); break;
                    case '★': grid[y][x] = '.'; items.add(new Item(x, y, '★', 'T')); break;
                    case '?': grid[y][x] = '.'; items.add(new Item(x, y, '?', 'M')); break;
                    default:  grid[y][x] = '.'; break;
                }
            }
        }

        playerX = fixFromInt(spawnX) + FIX_HALF;
        playerY = fixFromInt(spawnY) + FIX_HALF;
    }

    static String stripBorders(String s) {
        int start = 2;
        int end   = s.length() - 2;
        if (start > end) return "";
        return s.substring(start, end);
    }

    // =========================================================
    // Input Handling
    // =========================================================
    static void processInput() {
        try {
            while (reader.ready()) {
                int c = reader.read();
                if (c < 0) break;
                switch (c) {
                    case 'w': case 'W': keyW = true; break;
                    case 's': case 'S': keyS = true; break;
                    case 'a': case 'A': keyA = true; break;
                    case 'd': case 'D': keyD = true; break;
                    case 'j': case 'J': keyTurnL = true; break;
                    case 'l': case 'L': keyTurnR = true; break;
                    case 'q': case 'Q': running = false; break;
                }
            }
        } catch (Exception e) {
            running = false;
        }
    }

    // =========================================================
    // Movement & Interactions
    // =========================================================
    static boolean isWallCell(int cx, int cy) {
        if (cx < 0 || cx >= mapW || cy < 0 || cy >= mapH) return true;
        char c = grid[cy][cx];
        return c == '#' || c == 'D';
    }

    static void setMessage(String s) {
        message = s;
        messageUntil = System.currentTimeMillis() + 2500;
    }

    static boolean canMoveTo(int fx, int fy) {
        int cx = fixToInt(fx), cy = fixToInt(fy);
        if (cx < 0 || cx >= mapW || cy < 0 || cy >= mapH) return false;
        char c = grid[cy][cx];
        if (c == '#') return false;
        if (c == 'D') {
            if (hasKey) {
                grid[cy][cx] = '.'; // Door consumed permanently
                setMessage("The heavy iron door unlocks and slides open!");
                return true;
            }
            setMessage("LOCKED! Find the KEY (◆) in the upper complex first.");
            return false;
        }
        return true;
    }

    static void movePlayer(int dx, int dy) {
        int nx = playerX + dx;
        int ny = playerY + dy;
        if (canMoveTo(nx, playerY)) playerX = nx;
        if (canMoveTo(playerX, ny)) playerY = ny;
    }

    static void rotate(boolean left) {
        int c = ROT_COS, s = ROT_SIN;
        int ndx, ndy, npx, npy;
        if (left) {
            ndx =  fixMul(dirX, c) + fixMul(dirY, s);
            ndy = -fixMul(dirX, s) + fixMul(dirY, c);
            npx =  fixMul(planeX, c) + fixMul(planeY, s);
            npy = -fixMul(planeX, s) + fixMul(planeY, c);
        } else {
            ndx = fixMul(dirX, c) - fixMul(dirY, s);
            ndy = fixMul(dirX, s) + fixMul(dirY, c);
            npx = fixMul(planeX, c) - fixMul(planeY, s);
            npy = fixMul(planeX, s) + fixMul(planeY, c);
        }
        dirX = ndx; dirY = ndy; planeX = npx; planeY = npy;
    }

    // =========================================================
    // Game Logic Update
    // =========================================================
    static void update() {
        if (keyTurnL) rotate(true);
        if (keyTurnR) rotate(false);

        if (keyW) movePlayer( fixMul(dirX, MOVE_SPEED),  fixMul(dirY, MOVE_SPEED));
        if (keyS) movePlayer(-fixMul(dirX, MOVE_SPEED), -fixMul(dirY, MOVE_SPEED));

        int perpX = -dirY, perpY = dirX;
        if (keyA) movePlayer(-fixMul(perpX, MOVE_SPEED), -fixMul(perpY, MOVE_SPEED));
        if (keyD) movePlayer( fixMul(perpX, MOVE_SPEED),  fixMul(perpY, MOVE_SPEED));

        checkPickups();
        checkExit();

        keyW = keyA = keyS = keyD = keyTurnL = keyTurnR = false;

        // Door Proximity Hint
        int cx = fixToInt(playerX), cy = fixToInt(playerY);
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int nx = cx + dx, ny = cy + dy;
                if (nx < 0 || nx >= mapW || ny < 0 || ny >= mapH) continue;
                if (grid[ny][nx] == 'D' && Math.abs(dx) + Math.abs(dy) <= 2) {
                    if (hasKey) setMessage("Door (╬) ahead. Walk forward to unlock.");
                    else        setMessage("Door (╬) is locked! Find the KEY (◆) first.");
                    return;
                }
            }
        }
    }

    static void checkPickups() {
        for (Item it : items) {
            if (it.collected) continue;
            int ix = fixFromInt(it.x) + FIX_HALF;
            int iy = fixFromInt(it.y) + FIX_HALF;
            long dx = (long)ix - playerX;
            long dy = (long)iy - playerY;
            long d2 = dx * dx + dy * dy;
            long thresh = ((long)FIX_ONE * 5 / 8);
            thresh *= thresh;
            if (d2 < thresh) {
                it.collected = true;
                switch (it.kind) {
                    case 'K':
                        hasKey = true;
                        setMessage("KEY (◆) acquired! Proceed to the main division door (╬).");
                        break;
                    case 'T':
                        hasTreasure = true;
                        setMessage("TREASURE (★) claimed! Make your escape.");
                        break;
                    case 'M':
                        hasRelic = true;
                        setMessage("RELIC (?) activated! The ancient exit port is revealed.");
                        break;
                }
            }
        }
    }

    static void checkExit() {
        int cx = fixToInt(playerX), cy = fixToInt(playerY);
        if (cx < 0 || cx >= mapW || cy < 0 || cy >= mapH) return;
        if (grid[cy][cx] != 'X') return;

        // Exit can only be used if Relic is collected
        if (hasRelic) {
            won = true;
            running = false;
        } else {
            if (messageUntil < System.currentTimeMillis()) {
                setMessage("The exit point remains hidden. Find the RELIC (?) to reveal it.");
            }
        }
    }

    // =========================================================
    // Shading
    // =========================================================
    static char pickWallChar(long distFixed, int side) {
        int d = (int)(distFixed >> FIX_SHIFT);
        int idx;
        if      (d <= 2) idx = 0;
        else if (d <= 4) idx = 1;
        else if (d <= 7) idx = 2;
        else             idx = 3;
        if (side == 1) idx = Math.min(idx + 1, 3);
        final char[] shades = { '\u2588', '\u2593', '\u2592', '\u2591' };
        return shades[idx];
    }

    // =========================================================
    // Render Loop
    // =========================================================
    static void render() {
        int w = terminal.getWidth();
        int h = terminal.getHeight();
        if (w < 20) w = 80;
        if (h < 8)  h = 24;
        
        screenW = w;
        screenH = h;
        viewH   = screenH - 3; // Extra space reserved for details/notes
        if (viewH < 5) viewH = 5;

        if (screen == null || screen.length < viewH || screen[0].length < screenW) {
            screen = new char[viewH][screenW];
            zbuf   = new long[screenW];
        }

        int halfV = viewH / 2;
        for (int y = 0; y < halfV; y++) {
            char[] row = screen[y];
            for (int x = 0; x < screenW; x++) row[x] = ' ';
        }
        for (int y = halfV; y < viewH; y++) {
            char[] row = screen[y];
            for (int x = 0; x < screenW; x++) row[x] = '.';
        }

        // ---- 3D Raycasting ----
        for (int x = 0; x < screenW; x++) {
            int cameraX = fixDiv(fixFromInt(2 * x - screenW), fixFromInt(screenW));
            int rayDirX = dirX + fixMul(planeX, cameraX);
            int rayDirY = dirY + fixMul(planeY, cameraX);

            int mapX = fixToInt(playerX);
            int mapY = fixToInt(playerY);

            long deltaDistX = invDistFixed(rayDirX);
            long deltaDistY = invDistFixed(rayDirY);

            int stepX, stepY;
            long sideDistX, sideDistY;

            if (rayDirX < 0) {
                stepX = -1;
                long diff = (long)playerX - (long)fixFromInt(mapX);
                sideDistX = diff * deltaDistX / FIX_ONE;
            } else {
                stepX = 1;
                long diff = (long)fixFromInt(mapX + 1) - (long)playerX;
                sideDistX = diff * deltaDistX / FIX_ONE;
            }
            if (rayDirY < 0) {
                stepY = -1;
                long diff = (long)playerY - (long)fixFromInt(mapY);
                sideDistY = diff * deltaDistY / FIX_ONE;
            } else {
                stepY = 1;
                long diff = (long)fixFromInt(mapY + 1) - (long)playerY;
                sideDistY = diff * deltaDistY / FIX_ONE;
            }

            int side = 0;
            boolean hit = false;
            for (int iter = 0; iter < 200 && !hit; iter++) {
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX;
                    mapX += stepX;
                    side = 0;
                } else {
                    sideDistY += deltaDistY;
                    mapY += stepY;
                    side = 1;
                }
                if (isWallCell(mapX, mapY)) hit = true;
            }

            if (!hit) {
                zbuf[x] = Long.MAX_VALUE;
                continue;
            }

            long perpWallDist = (side == 0)
                ? sideDistX - deltaDistX
                : sideDistY - deltaDistY;
            if (perpWallDist < FIX_ONE / 2) perpWallDist = FIX_ONE / 2;
            zbuf[x] = perpWallDist;

            int lineHeight = (int)((long)viewH * FIX_ONE * 2 / (perpWallDist * 3));
            if (lineHeight < 1) lineHeight = 1;

            int drawStart = halfV - lineHeight / 2;
            int drawEnd   = halfV + lineHeight / 2;
            if (drawStart < 0)     drawStart = 0;
            if (drawEnd   > viewH) drawEnd   = viewH;

            char wallChar;
            if (mapX < 0 || mapX >= mapW || mapY < 0 || mapY >= mapH) {
                wallChar = pickWallChar(perpWallDist, side);
            } else {
                char cell = grid[mapY][mapX];
                if (cell == 'D') {
                    wallChar = '\u256C'; // ╬ Door
                } else {
                    wallChar = pickWallChar(perpWallDist, side);
                }
            }

            for (int y = drawStart; y < drawEnd; y++) {
                screen[y][x] = wallChar;
            }
        }

        // ---- Item Billboards ----
        double px = playerX / 65536.0;
        double py = playerY / 65536.0;
        double dx = dirX / 65536.0;
        double dy = dirY / 65536.0;
        double plx = planeX / 65536.0;
        double ply = planeY / 65536.0;
        double det = plx * dy - dx * ply;
        double invDet = (det == 0) ? 0 : 1.0 / det;

        for (Item it : items) {
            if (it.collected) continue;
            double ix = it.x + 0.5;
            double iy = it.y + 0.5;
            double rx = ix - px;
            double ry = iy - py;
            double tx = invDet * (dy * rx - dx * ry);
            double ty = invDet * (-ply * rx + plx * ry);
            if (ty <= 0.2) continue;

            int sx = (int)((screenW / 2.0) * (1 + tx / ty));
            if (sx < 0 || sx >= screenW) continue;
            if ((long)(ty * 65536.0) >= zbuf[sx]) continue;

            int sprH = (int)(viewH / ty / 2);
            if (sprH < 1) sprH = 1;
            int top = halfV - sprH / 2;
            int bot = top + sprH;
            if (top < 0)     top = 0;
            if (bot > viewH) bot = viewH;

            for (int y = top; y < bot; y++) screen[y][sx] = it.glyph;
        }

        // ---- Output Rendering ----
        StringBuilder sb = new StringBuilder(screenW * (viewH + 5));
        sb.append("\033[H");
        for (int y = 0; y < viewH; y++) {
            sb.append(screen[y], 0, screenW);
            sb.append("\r\n");
        }

        // HUD Line 1: Status
        String inv = String.format("[%s] KEY    [%s] TREASURE    [%s] RELIC",
                hasKey      ? "◆" : " ",
                hasTreasure ? "★" : " ",
                hasRelic    ? "?" : " ");
        sb.append(pad(inv, screenW)).append("\r\n");

        // HUD Line 2: Action Message or Controls
        String line2;
        if (System.currentTimeMillis() < messageUntil && !message.isEmpty()) {
            line2 = message;
        } else {
            line2 = "WASD: Move | J/L: Look | Q: Quit | Objective: Key -> Door -> Relic -> Exit";
        }
        sb.append(pad(line2, screenW)).append("\r\n");

        // Required Rule Note
        String note = "NOTE: You must find the RELIC (?) to reveal the exit point.";
        sb.append(pad(note, screenW));

        sb.append("\033[J");

        terminal.writer().print(sb);
        terminal.flush();
    }

    static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        if (sb.length() > width) sb.setLength(width);
        return sb.toString();
    }

    // =========================================================
    // Game Loop
    // =========================================================
    static void gameLoop() throws Exception {
        final long frameTime = 1_000_000_000L / 30;
        while (running) {
            long start = System.nanoTime();
            processInput();
            update();
            render();
            long elapsed   = System.nanoTime() - start;
            long remaining = frameTime - elapsed;
            if (remaining > 0) {
                Thread.sleep(remaining / 1_000_000L, (int)(remaining % 1_000_000L));
            }
        }
    }

    // =========================================================
    // Entry Point
    // =========================================================
    public static void main(String[] args) throws Exception {
        try {
            terminal = TerminalBuilder.builder().system(true).build();
            originalAttrs = terminal.getAttributes();
            terminal.enterRawMode();
            reader = terminal.reader();

            terminal.writer().print("\033[?25l");
            terminal.writer().print("\033[2J");
            terminal.writer().print("\033[H");
            terminal.flush();

            parseMap();
            gameLoop();

            if (won) {
                terminal.writer().print("\033[2J\033[H");
                terminal.writer().print("\n\n");
                terminal.writer().print("     ***************************************\n");
                
                // Ending logic based on Treasure collection
                if (hasTreasure) {
                    terminal.writer().print("     *           CONGRATULATIONS!          *\n");
                    terminal.writer().print("     *   YOU ESCAPED WITH THE TREASURE!    *\n");
                } else {
                    terminal.writer().print("     *        WON, BUT AT WHAT COST?       *\n");
                    terminal.writer().print("     *  YOU ESCAPED WITHOUT THE TREASURE!  *\n");
                }
                
                terminal.writer().print("     *                                     *\n");
                terminal.writer().print("     *        Thanks for playing.          *\n");
                terminal.writer().print("     ***************************************\n");
                terminal.flush();
                Thread.sleep(3000);
            }

        } finally {
            if (terminal != null) {
                try {
                    if (originalAttrs != null) terminal.setAttributes(originalAttrs);
                    terminal.writer().print("\033[0m");
                    terminal.writer().print("\033[?25h");
                    terminal.writer().print("\033[2J");
                    terminal.writer().print("\033[H");
                    terminal.flush();
                } catch (Exception ignored) {}
                terminal.close();
            }
        }
    }
}