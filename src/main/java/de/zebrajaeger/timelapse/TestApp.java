package de.zebrajaeger.timelapse;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.NativeLongByReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.blackdread.camerabinding.jna.EdsdkLibrary;
import org.blackdread.cameraframework.api.command.builder.ShootOption;
import org.blackdread.cameraframework.api.command.builder.ShootOptionBuilder;
import org.blackdread.cameraframework.api.constant.EdsObjectEvent;
import org.blackdread.cameraframework.api.constant.EdsdkError;
import org.blackdread.cameraframework.api.helper.factory.CanonFactory;
import org.blackdread.cameraframework.api.helper.logic.event.CameraObjectListener;
import org.blackdread.cameraframework.util.ReleaseUtil;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.blackdread.cameraframework.api.helper.factory.CanonFactory.edsdkLibrary;
import static org.blackdread.cameraframework.util.ErrorUtil.toEdsdkError;

@Slf4j
public class TestApp {

    private final EdsdkLibrary.EdsCameraRef.ByReference camera;
    private final EdsdkLibrary.EdsCameraRef cameraRef;

    public static void main(String[] args) throws IOException {
        TestApp testApp = null;
        try {
            testApp = new TestApp();
            testApp.doIt();
        } finally {
            if (testApp != null) {
                testApp.shutDown();
            }
        }
    }

    public TestApp() throws IOException {
        Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_MULTITHREADED);
        final EdsdkError error = toEdsdkError(edsdkLibrary().EdsInitializeSDK());
        assert (EdsdkError.EDS_ERR_OK == error);
        try {
            camera = getFirstCamera();
        } catch (IOException e) {
            shutDown();
            throw e;
        }
        assertNoError(edsdkLibrary().EdsOpenSession(camera.getValue()));
        cameraRef = camera.getValue();
    }

    public EdsdkLibrary.EdsCameraRef.ByReference getFirstCamera() throws IOException {
        final EdsdkLibrary.EdsCameraListRef.ByReference cameraListRef = new EdsdkLibrary.EdsCameraListRef.ByReference();
        assertNoError(edsdkLibrary().EdsGetCameraList(cameraListRef));
        try {
            final NativeLongByReference outRef = new NativeLongByReference();
            assertNoError(edsdkLibrary().EdsGetChildCount(cameraListRef.getValue(), outRef));

            final long numCams = outRef.getValue().longValue();
            if (numCams <= 0) {
                throw new IOException("No camera connected");
            }

            final EdsdkLibrary.EdsCameraRef.ByReference cameraRef = new EdsdkLibrary.EdsCameraRef.ByReference();
            assertNoError(edsdkLibrary().EdsGetChildAtIndex(cameraListRef.getValue(), new NativeLong(0), cameraRef));
            return cameraRef;
        } finally {
            ReleaseUtil.release(cameraListRef);
        }
    }


    public void shutDown() {
        try {
            assertNoError(edsdkLibrary().EdsCloseSession(camera.getValue()));
        } finally {
            ReleaseUtil.release(camera);
        }
        assertNoError(toEdsdkError(edsdkLibrary().EdsTerminateSDK()));
    }

    public void doIt() {

        CanonFactory.cameraObjectEventLogic().registerCameraObjectEvent(cameraRef);

        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String dirId = df.format(LocalDateTime.now());
        File tempPath = new File("R:/", dirId);
        File targetPath = new File("D:/", dirId);
        long n = 60 * 60 * 12;
        long periodMs = 1000;

        if (!tempPath.mkdirs()) {
            throw new IllegalStateException("Could not create directory " + tempPath.getAbsolutePath());
        }
        if (!targetPath.mkdirs()) {
            throw new IllegalStateException("Could not create directory " + targetPath.getAbsolutePath());
        }

        AtomicBoolean running = new AtomicBoolean(true);
        final LinkedList<File> toMove = new LinkedList<>();
        final LinkedList<ShootOption> toDownload = new LinkedList<>();

        Executor executor = Executors.newFixedThreadPool(2);
        executor.execute(() -> {
            // Move files loop
            for (; running.get() || !toMove.isEmpty(); ) {
                try {
                    if (toMove.isEmpty()) {
                        Thread.sleep(10);
                    } else {
                        File file = toMove.pollFirst();
                        if (file != null) {
                            log.warn("-> Move File {} to {}", file.getAbsolutePath(), targetPath);
                            FileUtils.moveFileToDirectory(file, targetPath, false);
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        executor.execute(() -> {
            try {
                // Shot loop
                long shotAt = (System.currentTimeMillis() / 1000 + 1) * 1000;
                for (int i = 0; i < n; ++i) {
                    long t = shotAt - System.currentTimeMillis();
                    if (t < 0) {
                        log.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        log.error("Wait for {}ms", (shotAt - System.currentTimeMillis()));
                        log.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    } else {
                        log.warn("Wait for {}ms", (shotAt - System.currentTimeMillis()));
                    }
                    while (shotAt > System.currentTimeMillis()) {
                        long w = Math.max(1, (shotAt - System.currentTimeMillis()) / 2);
                        Thread.sleep(w);
                    }
                    shotAt += periodMs;
                    ShootOption so = new ShootOptionBuilder()
                            .setShootWithAF(false)
                            .setShootWithV0(false)

                            .setWaitForItemDownloadEvent(false)
                            .setFetchEvents(false)

                            .setBusyWaitMillis(1)
                            .setFolderDestination(tempPath)
                            .setFilename(String.format("%06d", i))
                            .build();
                    toDownload.add(so);
                    long s1 = System.currentTimeMillis();
                    log.warn("Start shot {}/{}  remaining: {}", i + 1, n, remainingTimeFormatterS(n - i));
                    List<File> images = CanonFactory.shootLogic().shoot(cameraRef, so);
                    long s2 = System.currentTimeMillis();
                    log.warn("Done shot in {}ms: {}", s2 - s1, images);
                    toMove.addAll(images);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            running.set(false);
        });

        CanonFactory.cameraObjectEventLogic().registerCameraObjectEvent(cameraRef);
        final CameraObjectListener cameraObjectListener = event -> {
            if (event.getObjectEvent() == EdsObjectEvent.kEdsObjectEvent_DirItemCreated
                    || event.getObjectEvent() == EdsObjectEvent.kEdsObjectEvent_DirItemRequestTransfer) {

                final EdsdkLibrary.EdsDirectoryItemRef itemRef = new EdsdkLibrary.EdsDirectoryItemRef(event.getBaseRef().getPointer());

                try {
                    ShootOption so = toDownload.pollFirst();
                    log.warn("Start download {}", so.getFilename().orElse("???"));
                    final File downloadedFile = CanonFactory.fileLogic().download(itemRef, so.getFolderDestination().orElse(null), so.getFilename().orElse(null));
                    log.warn("Done download");
                    toMove.add(downloadedFile);
                } catch (RuntimeException e) {
                    CanonFactory.fileLogic().downloadCancel(itemRef);
                }
            }
        };
        CanonFactory.cameraObjectEventLogic().addCameraObjectListener(cameraRef, cameraObjectListener);

        // Event loop
        for (; running.get(); ) {
            CanonFactory.edsdkLibrary().EdsGetEvent();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                break;
            }
        }

    }

    private String remainingTimeFormatterS(long remaining) {
        long h = remaining / (60 * 60);
        long m = (remaining - (h * 60 * 60)) / 60;
        long s = remaining - (h * 60 * 60) - (m * 60);
        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(h).append("h ");
        }
        if (h > 0 || m > 0) {
            sb.append(m).append("m ");
        }
        sb.append(s).append("s ");
        return sb.toString();
    }

    private void assertNoError(final EdsdkError error) {
        assert (EdsdkError.EDS_ERR_OK == error);
    }

    private void assertNoError(final NativeLong error) {
        assertNoError(toEdsdkError(error));
    }
}
