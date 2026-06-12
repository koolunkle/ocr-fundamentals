package com.example.ocr.core.translator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.util.NDImageUtils;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.translate.NoBatchifyTranslator;
import ai.djl.translate.TranslatorContext;

/**
 * 한국어 사전 기반 텍스트 인식 Translator.
 *
 * <p>PpWordRecognitionTranslator는 사전 파일을 trim()으로 읽어 공백 줄이 누락되고,
 * 그 결과 인덱스가 1 밀려 IndexOutOfBoundsException이 발생한다.
 * 이 클래스는 trim 없이 사전을 읽어 인덱스 정합성을 보장한다.</p>
 */
public class KoreanRecognitionTranslator implements NoBatchifyTranslator<Image, String> {

    private static final int TARGET_HEIGHT = 32;

    private List<String> table;

    @Override
    public void prepare(TranslatorContext ctx) throws IOException {
        table = new ArrayList<>();
        table.add("blank"); // index 0: CTC blank

        try (var reader = new BufferedReader(new InputStreamReader(
                ctx.getModel().getArtifact("korean_dict.txt").openStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                table.add(line); // trim 하지 않음
            }
        }

        table.add(" "); // 마지막: 공백 문자
    }

    @Override
    public NDList processInput(TranslatorContext ctx, Image image) {
        NDArray array = image.toNDArray(ctx.getNDManager());
        int[] size = resizeKeepRatio(image.getWidth(), image.getHeight());
        array = NDImageUtils.resize(array, size[1], size[0]);
        array = NDImageUtils.toTensor(array).sub(0.5f).div(0.5f);
        array = array.expandDims(0);
        return new NDList(array);
    }

    @Override
    public String processOutput(TranslatorContext ctx, NDList list) {
        var sb = new StringBuilder();
        NDArray output = list.singletonOrThrow();
        long[] indices = output.get(new long[]{0}).argMax(1).toLongArray();

        // CTC Greedy Decoding: blank(0) 무시, 연속 동일 인덱스 병합
        long prev = -1;
        for (long idx : indices) {
            if (idx > 0 && idx != prev) {
                sb.append(table.get((int) idx));
            }
            prev = idx;
        }
        return sb.toString();
    }

    /** 높이 32px 기준, 종횡비 유지, 너비를 32의 배수로 맞춘다. */
    private int[] resizeKeepRatio(double width, double height) {
        double ratio = TARGET_HEIGHT / height;
        int w = (Math.max(TARGET_HEIGHT, (int) (width * ratio)) / TARGET_HEIGHT) * TARGET_HEIGHT;
        return new int[]{TARGET_HEIGHT, w};
    }
}
