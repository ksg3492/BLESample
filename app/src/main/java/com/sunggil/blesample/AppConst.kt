package com.sunggil.blesample

import android.os.Environment

class AppConst {
    class SERVER_TO_CLIENT {
        companion object {
            val MELON_CHART_LIST                                = 0x0010
            val MELON_CHART_LIST_THUMB                          = 0x0020
            val PAGE_INDEX                                      = 0x0030
            val MELON_STREAMING_FILE_START                      = 0x0040
            val MELON_STREAMING_FILE_DOWNLOADING                = 0x0050
            val MELON_STREAMING_FILE_DONE                       = 0x0060
        }
    }

    class CLIENT_TO_SERVER {
        companion object {
            val GET_MELON_CHART_LIST                          = 0x0100
            val GET_MELON_STREAMING                           = 0x0101
        }
    }

    class COMMON {
        companion object {
            var IS_CLIENT                                     = true
            val MIN_PRELOAD_BUFFER                            = 5 // 5%

            val RESTART_CONNECTION_SERVER                     = 0x0200
            val RESTART_DATATRANSFER_THREAD                   = 0x0201

            val LOADING_DIALOG_SHOW                           = 0x0202
            val LOADING_DIALOG_DISMISS                        = 0x0203
            val DOWNLOAD_PERCENTAGE_UI                        = 0x0204
            val PRELOAD_PERCENTAGE_UI                         = 0x0205
        }
    }

    class ERROR {
        companion object {
            val RESPONSE_NOT_SUCCESS              = "response is not Success"
            val RESPONSE_FAIL                     = "response is fail"
        }
    }

    class RETROFIT {
        companion object {
            val DOWNLOAD_MP3_FOLDER_PATH            = "${Environment.getExternalStorageDirectory()}/BLESample/MP3/"
        }
    }
}