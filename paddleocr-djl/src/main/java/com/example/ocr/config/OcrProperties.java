package com.example.ocr.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ocr")
public class OcrProperties {

    private final Model model;
    private final Options options;
    private final Rendering rendering;
    private final Paths paths;

    @ConstructorBinding
    public OcrProperties(
            @DefaultValue Model model,
            @DefaultValue Options options,
            @DefaultValue Rendering rendering,
            @DefaultValue Paths paths) {
        this.model = model;
        this.options = options;
        this.rendering = rendering;
        this.paths = paths;
    }

    public Model getModel() { return model; }
    public Options getOptions() { return options; }
    public Rendering getRendering() { return rendering; }
    public Paths getPaths() { return paths; }

    public static class Model {
        private final String baseDir;
        private final String detDir;
        private final String recDir;
        private final String clsDir;

        public Model(
                @DefaultValue("models/paddleocr") String baseDir,
                @DefaultValue("det") String detDir,
                @DefaultValue("rec") String recDir,
                @DefaultValue("cls") String clsDir) {
            this.baseDir = baseDir;
            this.detDir = detDir;
            this.recDir = recDir;
            this.clsDir = clsDir;
        }

        public String getBaseDir() { return baseDir; }
        public String getDetDir() { return detDir; }
        public String getRecDir() { return recDir; }
        public String getClsDir() { return clsDir; }

        public Path resolveDetPath() { return Path.of(baseDir).resolve(detDir); }
        public Path resolveRecPath() { return Path.of(baseDir).resolve(recDir); }
        public Path resolveClsPath() { return Path.of(baseDir).resolve(clsDir); }
    }

    public static class Options {
        private final double detDbThresh;
        private final double detDbBoxThresh;
        private final double detDbUnclipRatio;
        private final double clsThreshold;
        private final double minConfidence;
        private final int recPadding;

        public Options(
                @DefaultValue("0.3") double detDbThresh,
                @DefaultValue("0.5") double detDbBoxThresh,
                @DefaultValue("1.6") double detDbUnclipRatio,
                @DefaultValue("0.5") double clsThreshold,
                @DefaultValue("0.4") double minConfidence,
                @DefaultValue("10") int recPadding) {
            this.detDbThresh = detDbThresh;
            this.detDbBoxThresh = detDbBoxThresh;
            this.detDbUnclipRatio = detDbUnclipRatio;
            this.clsThreshold = clsThreshold;
            this.minConfidence = minConfidence;
            this.recPadding = recPadding;
        }

        public double getDetDbThresh() { return detDbThresh; }
        public double getDetDbBoxThresh() { return detDbBoxThresh; }
        public double getDetDbUnclipRatio() { return detDbUnclipRatio; }
        public double getClsThreshold() { return clsThreshold; }
        public double getMinConfidence() { return minConfidence; }
        public int getRecPadding() { return recPadding; }
    }

    public static class Rendering {
        private final int dpi;
        private final boolean isDebug;

        public Rendering(
                @DefaultValue("300") int dpi,
                @DefaultValue("false") boolean isDebug) {
            this.dpi = dpi;
            this.isDebug = isDebug;
        }

        public int getDpi() { return dpi; }
        public boolean isDebug() { return isDebug; }
    }

    public static class Paths {
        private final String debugDir;

        public Paths(@DefaultValue("debug_images") String debugDir) {
            this.debugDir = debugDir;
        }

        public String getDebugDir() { return debugDir; }
    }
}
