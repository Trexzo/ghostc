package dev.ghostfusion.core;

import java.util.Locale;

/** Typed validated setting. Storage and public mutation use the same parser. */
public abstract class Setting<T> {
    private final String id;
    private T value;
    protected Setting(String id, T initialValue) {
        this.id = ModuleRegistry.validateId(id);
        this.value = initialValue;
    }
    public final String id() { return id; }
    public final T get() { return value; }
    public final void setFromText(String text) { value = parse(text); }
    public final String serialize() { return format(value); }
    public abstract T parse(String text);
    protected abstract String format(T val);

    public static final class Bool extends Setting<Boolean> {
        public Bool(String id, boolean initial) { super(id, initial); }
        public Boolean parse(String s) {
            if ("true".equalsIgnoreCase(s)) return true;
            if ("false".equalsIgnoreCase(s)) return false;
            throw new IllegalArgumentException("Invalid boolean: " + s);
        }
        protected String format(Boolean v) { return v.toString(); }
    }
    public static final class Choice extends Setting<String> {
        private final java.util.Set<String> allowed;
        public Choice(String id, String initial, String... choices) {
            super(id, initial);
            if (choices == null || choices.length == 0) throw new IllegalArgumentException("No choices");
            allowed = new java.util.LinkedHashSet<String>(java.util.Arrays.asList(choices));
            if (allowed.contains(null) || !allowed.contains(initial)) throw new IllegalArgumentException("Invalid initial choice");
        }
        public java.util.List<String> options() {
            return java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(allowed));
        }
        public String parse(String s) {
            if (!allowed.contains(s)) throw new IllegalArgumentException("Invalid choice for " + id());
            return s;
        }
        protected String format(String v) { return v; }
    }
    public static final class Number extends Setting<Double> {
        private final double min, max;
        public Number(String id, double initial, double min, double max) {
            super(id, initial);
            if (!Double.isFinite(min) || !Double.isFinite(max) ||
                !Double.isFinite(initial) || min > max || initial < min || initial > max)
                throw new IllegalArgumentException("Invalid range: " + id);
            this.min = min; this.max = max;
        }
        public double min() { return min; }
        public double max() { return max; }
        public Double parse(String s) {
            final double n = Double.parseDouble(s);
            if (!Double.isFinite(n) || n < min || n > max)
                throw new IllegalArgumentException("Out of range: " + id());
            return n;
        }
        protected String format(Double v) { return String.format(Locale.ROOT, "%s", v); }
    }
}
