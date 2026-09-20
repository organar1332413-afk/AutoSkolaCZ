package cz.autoskola.app.ui
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import cz.autoskola.app.R
import cz.autoskola.domain.MediaReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
/** Imported, checksum-verified local images. No remote image fetching. */
@Composable fun LocalMedia(media:MediaReference) {
    val root=File(LocalContext.current.filesDir,"content")
    val loaded by produceState<Pair<Boolean,android.graphics.Bitmap?>>(false to null,media.path) {
        value=withContext(Dispatchers.IO) {
            val file=File(root,media.path).canonicalFile
            val bitmap=if(file.path.startsWith(root.canonicalPath+File.separator) && media.mimeType in listOf("image/png","image/jpeg","image/webp")) {
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file.path,bounds)
                var sample=1;while(bounds.outWidth/sample>2048 || bounds.outHeight/sample>2048) sample*=2
                BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample })
            } else null
            true to bitmap
        }
    }
    if(!loaded.first) CircularProgressIndicator()
    else loaded.second?.let { Image(it.asImageBitmap(),text(R.string.lesson_situation),Modifier.fillMaxWidth()) } ?: Note(text(R.string.lesson_media_pending))
}
