package canyonuhc.util;

public final class MutableDouble {
    private double value;

    public MutableDouble(double value) {
        this.value = value;
    }

    public MutableDouble() {
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public void addValue(double value) {
        this.value += value;
    }
}
