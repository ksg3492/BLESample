package com.sunggil.blesample

import android.os.Environment

class AppConst {
    class SERVER_TO_CLIENT {
        companion object {
            val MELON_CHART_LIST                                = 0x0001
            val MELON_CHART_LIST_THUMB                          = 0x0002
            val PAGE_INDEX                                      = 0x0003
            val MELON_STREAMING_FILE_START                      = 0x0004
            val MELON_STREAMING_FILE_DOWNLOADING                = 0x0005
            val MELON_STREAMING_FILE_DONE                       = 0x0006
            val YOUTUBE_HOT_LIST                                = 0x0007
            val YOUTUBE_HOT_LIST_THUMB                          = 0x0008
            val YOUTUBE_STREAMING_FILE_START                    = 0x0009
            val YOUTUBE_STREAMING_FILE_DOWNLOADING              = 0x0010
            val YOUTUBE_STREAMING_FILE_DONE                     = 0x0011
        }
    }

    class CLIENT_TO_SERVER {
        companion object {
            val GET_MELON_CHART_LIST                            = 0x0100
            val GET_MELON_STREAMING                             = 0x0101
            val GET_YOUTUBE_CHART_LIST                          = 0x0102
            val GET_YOUTUBE_STREAMING                           = 0x0103
            val STOP_UPLOAD_STREAMING                           = 0x0104
        }
    }

    class COMMON {
        companion object {
            var IS_CLIENT                                     = true
            val MIN_PRELOAD_BUFFER_PERCENT                    = 10 // 5%
            var MIN_PRELOAD_BUFFER_SIZE                  = 200 * 1024   //150 kByte

            val PING                                          = 0x0200

            val RESTART_CONNECTION_SERVER                     = 0x0201
            val RESTART_DATATRANSFER_THREAD                   = 0x0202

            val LOADING_DIALOG_SHOW                           = 0x0203
            val LOADING_DIALOG_DISMISS                        = 0x0204
            val DOWNLOAD_PERCENTAGE_UI                        = 0x0205
            val DOWNLOAD_SPEED_UI                             = 0x0206
            val PRELOAD_PERCENTAGE_UI                         = 0x0207
            val DOWNLOADED_SIZE_UI                            = 0x0208
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
            val DOWNLOAD_SERVER_FOLDER_PATH            = "${Environment.getExternalStorageDirectory()}/BLESample/Server/"
            val DOWNLOAD_MP3_FOLDER_PATH            = "${Environment.getExternalStorageDirectory()}/BLESample/MP3/"
            val DOWNLOAD_MP4_FOLDER_PATH            = "${Environment.getExternalStorageDirectory()}/BLESample/MP4/"
        }
    }
}