package com.bihe0832.android.lib.install.splitapk

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import com.bihe0832.android.lib.log.ZLog
import com.bihe0832.android.lib.thread.ThreadManager
import java.io.*
import java.util.*
import kotlin.collections.ArrayList

/**
 * Author: waynescliu
 * Date: 2020/8/20
 * Description:split apks install,无需root，但是对于miui，开发者模式中的miui优化会导致安装失败，建议关闭
 */
object SplitApksInstallHelper {
    private const val TAG = "SplitApksInstallHelper:::"
    private var mBroadcastReceiver: SplitApksInstallBroadcastReceiver? = null
    private var mPackageInstaller: PackageInstaller? = null
    private var mContext: Context? = null

    interface SplitApksInstallCb {
        fun onInstallSuccess()
        fun onInstallFailed()
    }

    private fun init(context: Context) {
        mContext = context
        mPackageInstaller = context.packageManager.packageInstaller
        mBroadcastReceiver = SplitApksInstallBroadcastReceiver(context)
        mBroadcastReceiver?.addEventObserver(object : SplitApksInstallBroadcastReceiver.EventObserver {
            override fun onConfirmationPending() {
            }

            override fun onInstallationSucceeded() {
                try {
                    context.unregisterReceiver(mBroadcastReceiver)
                } catch (e: java.lang.Exception) {
                    ZLog.e("unregisterReceiver failed:${e.message}")
                }
            }

            override fun onInstallationFailed() {
                try {
                    context.unregisterReceiver(mBroadcastReceiver)
                } catch (e: java.lang.Exception) {
                    ZLog.e("unregisterReceiver failed:${e.message}")
                }
            }
        })
        mBroadcastReceiver?.let {
            context.registerReceiver(it, IntentFilter(it.intentFilterFlag))
        }
    }

    fun installApk(context: Context, fileDir: File?) :Boolean {
        init(context)
        fileDir?.let {
            if (it.isDirectory) {
                val files = ArrayList<String>()
                it.listFiles().forEach { file ->
                    ZLog.d("$TAG fileName:${it.name},absolutePath:${file.absolutePath}")
                    files.add(file.absolutePath)
                }
                ThreadManager.getInstance().start {
                    installApk(files)
                }
                return true
            }
        }
        return false
    }

    private fun installApk(files: ArrayList<String>): Int {

        val nameSizeMap = HashMap<String, Long>()
        val filenameToPathMap = HashMap<String, String>()
        var totalSize: Long = 0
        var sessionId = 0
        try {
            for (file in files) {
                val listOfFile = File(file)
                if (listOfFile.isFile) {
                    ZLog.d("$TAG installApk: " + listOfFile.name)
                    nameSizeMap[listOfFile.name] = listOfFile.length()
                    filenameToPathMap[listOfFile.name] = file
                    totalSize += listOfFile.length()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        val installParams = makeSessionParams(totalSize)
        try {
            sessionId = runInstallCreate(installParams)
            for ((key, value) in nameSizeMap) {
                runInstallWrite(value, sessionId, key, filenameToPathMap[key])
            }
            if (doCommitSession(sessionId)
                    != PackageInstaller.STATUS_SUCCESS) {
            }
            ZLog.d("$TAG Success")
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        return sessionId
    }

    private fun runInstallCreate(sessionParams: SessionParams): Int {
        val sessionId = doCreateSession(sessionParams)
        ZLog.d("$TAG Success: created install session [$sessionId]")
        return sessionId
    }

    private fun doCreateSession(params: SessionParams?): Int {
        var sessionId = 0
        try {
            if (params == null) {
                ZLog.d(TAG, "doCreateSession: !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!param is null")
            }
            sessionId = mPackageInstaller!!.createSession(params)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return sessionId
    }

    private fun runInstallWrite(size: Long, sessionId: Int, splitName: String, path: String?): Int {
        var sizeBytes: Long = -1
        sizeBytes = size
        return doWriteSession(sessionId, path, sizeBytes, splitName)
    }


    private fun doWriteSession(sessionId: Int, inPath: String?, sizeBytes: Long, splitName: String): Int {
        var inPath = inPath
        var sizeBytes = sizeBytes
        if ("-" == inPath) {
            inPath = null
        } else if (inPath != null) {
            val file = File(inPath)
            if (file.isFile) {
                sizeBytes = file.length()
            }
        }
        var session: PackageInstaller.Session? = null
        var `in`: InputStream? = null
        var out: OutputStream? = null
        return try {
            session = mPackageInstaller?.openSession(sessionId)
            if (inPath != null) {
                `in` = FileInputStream(inPath)
            }
            out = session?.openWrite(splitName, 0, sizeBytes)
            var total = 0
            val buffer = ByteArray(65536)
            var c: Int
            while (`in`!!.read(buffer).also { c = it } != -1) {
                total += c
                out?.write(buffer, 0, c)
            }
            session?.fsync(out)
            ZLog.d("$TAG Success: streamed $total bytes")
            PackageInstaller.STATUS_SUCCESS
        } catch (e: IOException) {
            ZLog.d("$TAG Error: failed to write; " + e.message)
            PackageInstaller.STATUS_FAILURE
        } finally {
            try {
                out?.close()
                `in`?.close()
                session?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun doCommitSession(sessionId: Int): Int {
        var session: PackageInstaller.Session? = null
        return try {
            try {
                session = mPackageInstaller?.openSession(sessionId)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val callbackIntent = Intent(mBroadcastReceiver!!.intentFilterFlag)
            val pendingIntent = PendingIntent.getBroadcast(mContext, 0, callbackIntent, 0)
            session?.commit(pendingIntent.intentSender)
            session?.close()
            ZLog.d("$TAG install request sent")
            ZLog.d("$TAG doCommitSession: " + mPackageInstaller?.mySessions)
            ZLog.d("$TAG doCommitSession: after session commit ")
            1
        } finally {
            session?.close()
        }
    }

    private fun makeSessionParams(totalSize: Long): SessionParams {
        val sessionParams = SessionParams(SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sessionParams.setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        sessionParams.setSize(totalSize)
        return sessionParams
    }
}