package cz.autoskola.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMediaTest {
    @Test fun routesStaticImages() {
        assertEquals(LocalMediaKind.IMAGE, localMediaKind("image/png"))
        assertEquals(LocalMediaKind.IMAGE, localMediaKind("image/jpeg"))
        assertEquals(LocalMediaKind.IMAGE, localMediaKind("image/webp"))
    }

    @Test fun routesGifAsAnimatedImage() {
        assertEquals(LocalMediaKind.ANIMATED_IMAGE, localMediaKind("image/gif"))
    }

    @Test fun routesMp4AsVideo() {
        assertEquals(LocalMediaKind.VIDEO, localMediaKind("video/mp4"))
    }

    @Test fun rejectsUnsupportedMedia() {
        assertEquals(LocalMediaKind.UNSUPPORTED, localMediaKind("application/octet-stream"))
    }
}
