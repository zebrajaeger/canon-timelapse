package de.zebrajaeger.timelapse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

class TestAppConsole {
    ConsoleApp consoleApp = null;

    @BeforeEach
    void setUp() throws IOException {
        consoleApp = new ConsoleApp();
    }

    @AfterEach
    void tearDown() {
        if (consoleApp != null) {
            consoleApp.shutDown();
        }
    }

    @Disabled("Only run manually")
    @Test
    void doShotsWithTempDir() {
        String dirName = OptionParser.createDirName();
        Timelapse config = Timelapse.builder()
                .numberOfPictures(10)
                .tempPath(new File("R:/", dirName))
                .targetPath(new File("D:/", dirName))
                .build();
        consoleApp.doShots(config);
    }

    @Disabled("Only run manually")
    @Test
    void doShotsDirectToTargetDir() {
        String dirName = OptionParser.createDirName();
        Timelapse config = Timelapse.builder()
                .numberOfPictures(10)
                .targetPath(new File("D:/", dirName))
                .build();
        consoleApp.doShots(config);
    }
}