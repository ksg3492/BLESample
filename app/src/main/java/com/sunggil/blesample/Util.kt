package com.sunggil.blesample

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.text.TextUtils
import android.webkit.MimeTypeMap
import java.io.*
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class Util {
    companion object {
        fun checkMD5(md5 : String, updateFile : File?) : Boolean {
            if (TextUtils.isEmpty(md5) || updateFile == null) {
                return false;
            }

            var calculatedDigest = calculateMD5(updateFile);
            if (calculatedDigest == null) {
                return false;
            }

            return calculatedDigest.equals(md5, true);
        }

        fun checkMD5(md5 : String, md5_ : String) : Boolean {
            if (TextUtils.isEmpty(md5) || TextUtils.isEmpty(md5_)) {
                return false;
            }

            return md5.equals(md5_, true);
        }

        fun calculateMD5(updateFile : File) : String? {
            var digest : MessageDigest
            try {
                digest = MessageDigest.getInstance("MD5");
            } catch (e : NoSuchAlgorithmException) {
                return null
            }

            var input : InputStream;
            try {
                input = FileInputStream(updateFile)
            } catch (e : FileNotFoundException) {
                return null;
            }

            var buffer = ByteArray(1024);
            var read : Int
            try {
                while (true) {
                    read = input.read(buffer)
                    if (read == -1) { break }
                    digest.update(buffer, 0, read)
                }
                var md5sum = digest.digest()
                var bigInt = BigInteger(1, md5sum)
                var output = bigInt.toString(16)
                // Fill to 32 chars
                output = String.format("%32s", output).replace(' ', '0')
                return output.toUpperCase()
            } catch (e : IOException) {
                throw RuntimeException("Unable to process file for MD5", e);
            } finally {
                try {
                    input.close()
                } catch (e : IOException) {
                }
            }
        }

        fun getByte(output : RandomAccessFile) : Long {
            var file_size = 0L
            try {
                file_size = output.length()
                output.seek(file_size)
            } catch (e : IOException) {
                e.printStackTrace()
            } catch (e : Exception) {
                e.printStackTrace()
            }
            return file_size
        }

        fun convertMMSS(sec : Int) : String {
            val hh = sec / 60 / 60

            if (hh >= 1) {
                var hhmmss = "0:00:00"
                try {
                    val hour = sec / 60 / 60;
                    val minute = (sec - hour * 60 * 60) / 60;
                    val second = sec % 60;
                    hhmmss = String.format("%d", hour) + ":" + String.format("%02d", minute) + ":" + String.format("%02d", second);
                } catch (e : Exception) {
                }

                return hhmmss;
            } else {
                var mmss = "00:00";
                try {
                    val minute = sec / 60;
                    val second = sec % 60;
                    mmss = String.format("%02d", minute) + ":" + String.format("%02d", second);
                } catch (e : Exception) {
                }

                return mmss;
            }
        }

        fun getMimeType(c : Context, uri : Uri) : String? {
            var mimeType : String?
            if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
                val cr = c.getContentResolver()
                mimeType = cr.getType(uri)
            } else {
                val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase())
            }
            return mimeType
        }

        fun isMainLooper() : Boolean {
            return Looper.myLooper() == Looper.getMainLooper()
        }
    }



}