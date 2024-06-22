package de.zebrajaeger.timelapse;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.File;
import java.util.function.Function;


@Builder
@Getter
@ToString
public class Timelapse {
    @Builder.Default
    private long numberOfPictures = 60;

    @Builder.Default
    private long periodTimeMs = 1000;

    @Builder.Default
    private File targetPath = new File(".");

    @Builder.Default
    private File tempPath = null;

    @Builder.Default
    @ToString.Exclude
    private Function<Long, String> filenameGenerator = nr -> String.format("%06d", nr);
}
