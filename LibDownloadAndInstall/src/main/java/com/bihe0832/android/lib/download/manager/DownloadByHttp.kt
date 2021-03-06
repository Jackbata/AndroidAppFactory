package com.bihe0832.android.lib.download.manager

import android.content.Context
import com.bihe0832.android.lib.download.DownloadItem
import com.bihe0832.android.lib.download.DownloadListener
import com.bihe0832.android.lib.download.R
import com.bihe0832.android.lib.request.HTTPRequestUtils
import com.bihe0832.android.lib.thread.ThreadManager
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL


/**
 *
 * @author zixie code@bihe0832.com
 * Created on 2020-01-10.
 * Description: Description
 *
 */

class DownloadByHttp : DownloadWrapper() {

    var mCurrentConnectList = HashMap<String, HttpURLConnection>()

    override fun goDownload(context: Context, info: DownloadItem, downloadListener: DownloadListener?) {
        mCurrentDownloadList[info.downloadURL] = info
        ThreadManager.getInstance().start {
            var count: Int
            var input: BufferedInputStream? = null
            var finalFileName = applicationContext!!.getExternalFilesDir(applicationContext!!.getString(R.string.lib_bihe0832_file_folder)).absolutePath + "/" + "temp_" + System.currentTimeMillis() + "_" + info.fileName
            val output = FileOutputStream(finalFileName)
            try {
                val url = URL(info.downloadURL)
                val conection = url.openConnection() as HttpURLConnection
                mCurrentConnectList[info.downloadURL] = conection
                conection.connect()
                val lenghtOfFile = HTTPRequestUtils.getContentLength(conection)
                input = BufferedInputStream(url.openStream(), 8192)

                val data = ByteArray(1024)
                var total: Long = 0

                do {
                    count = input.read(data)
                    if (count == -1) break
                    total += count.toLong()
                    mCurrentDownloadList[info.downloadURL]?.let { downloadItem ->
                        downloadItem.downloadNotifyListenerList.forEach {
                            ThreadManager.getInstance().runOnUIThread{
                                it.onProgress(lenghtOfFile,total)
                            }
                        }
                    }
                    output.write(data, 0, count)
                } while (true)
                mCurrentDownloadList[info.downloadURL]?.let {
                    notifyDownload(it, finalFileName)
                }
                mCurrentDownloadList.remove(info.downloadURL)
                output.flush()
            } catch (e: Throwable) {
                e.printStackTrace()
                mCurrentDownloadList[info.downloadURL]?.let { downloadItem ->
                    downloadItem.downloadNotifyListenerList.forEach {
                        ThreadManager.getInstance().runOnUIThread{
                            it.onError(-7, "download with exception")
                        }
                    }
                }
                cancleDownload(info.downloadURL)
            } finally {
                try {
                    output.close()
                    input?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun cancleDownload(url: String) {
        mCurrentConnectList[url]?.let {
            it.disconnect()
        }
        stopDownload(url)
    }

    override fun onDestory() {

    }
}