package canyonuhc.uhc;

import canyonuhc.UHCPlugin;

public enum WorldBorderStage {
    FIRST(3064, 1532, 1800),
    SECOND(1532, 766),
    THIRD(766, 383, 900),
    FOURTH(383, 191, 800),
    FIFTH(191, 90, 700),
    SIX(90, 25, 500),
    FINAL(25, 10, 200),
    END(10, 0, 0);

    private final long startSize;
    private final long endSize;
    private final long time;

    WorldBorderStage(long startSize, long endSize, long time) {
        if (UHCPlugin.TEST_MODE) {
            startSize /= 4;
            endSize /= 4;
            time /= 4;
        }
        this.startSize = startSize;
        this.endSize = endSize;
        this.time = time;
    }

    WorldBorderStage(long startSize, long endSize) {
        this(startSize, endSize, startSize - endSize);
    }

    public float getStartSize() {
        return this.startSize;
    }

    public float getEndSize() {
        return this.endSize;
    }

    public long getTime(double size) {
        double blockPerTime = (this.startSize - this.endSize) / (double) this.time;
        return (long) (((long) size - this.endSize) / blockPerTime);
    }

    public WorldBorderStage getNextStage() {
        int next = this.ordinal() + 1;
        WorldBorderStage[] values = WorldBorderStage.values();

        if (next < values.length) {
            return values[next];
        }
        return END;
    }

    public static WorldBorderStage getStage(double size) {
        if (size <= FINAL.endSize) {
            return END;
        }
        for (WorldBorderStage worldBorderStage : WorldBorderStage.values()) {
            if (size <= worldBorderStage.startSize && size > worldBorderStage.endSize) {
                return worldBorderStage;
            }
        }
        return null;
    }
}
