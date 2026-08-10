package com.thiswuming.voicesummon.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.helpers.MessageFormatter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


object ModLogger {

    private const val LOG_DIR = "logs/voicesummon"
    private var logFileWriter: PrintWriter? = null
    private var fileLogEnabled: Boolean = false

    
    @Synchronized
    fun init(debug: Boolean, fileLog: Boolean) {
        fileLogEnabled = fileLog
        if (fileLog) {
            if (logFileWriter == null) openLogFile()
        } else {
            closeLogFile()
        }
    }

    private fun openLogFile() {
        try {
            val logDir = File(LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val logFile = File(logDir, "voicesummon-$dateStr.log")
            
            logFileWriter = PrintWriter(FileWriter(logFile, StandardCharsets.UTF_8, true), true)
            logFileWriter?.println("=== VoiceSummon Log Started at ${LocalDateTime.now()} ===")
        } catch (e: Exception) {
            System.err.println("[VoiceSummon] Failed to initialize file logging: ${e.message}")
        }
    }

    private fun closeLogFile() {
        try {
            logFileWriter?.println("=== VoiceSummon Log Ended at ${LocalDateTime.now()} ===")
            logFileWriter?.close()
        } catch (_: Exception) {
        }
        logFileWriter = null
    }

    fun getLogger(name: String): Logger {
        
        return ProxySLF4JLogger(LoggerFactory.getLogger(name), name)
    }

    
    private fun formatMessage(format: String, args: Array<out Any?>): String {
        return MessageFormatter.arrayFormat(format, args).message
    }

    @Synchronized
    fun writeToFile(level: String, name: String, message: String) {
        if (!fileLogEnabled || logFileWriter == null) return
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
            logFileWriter?.println("[$timestamp] [$level] [$name] $message")
        } catch (_: Exception) {
            
        }
    }

    
    @Synchronized
    fun writeRecognizedText(text: String) {
        writeTextFile("识别：$text")
    }

    
    @Synchronized
    fun writeSummonSuccess(speaker: String, mobName: String, count: Int, target: String) {
        writeTextFile("召唤成功：$speaker 召唤 $mobName x$count（目标：$target）")
    }

    
    private fun writeTextFile(content: String) {
        try {
            val logDir = File(LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val textFile = File(logDir, "text.txt")
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            
            FileWriter(textFile, StandardCharsets.UTF_8, false).use { writer ->
                writer.write("[$timestamp] $content\n")
            }
        } catch (_: Exception) {
            
        }
    }

    
    @Synchronized
    fun clearLog() {
        if (!fileLogEnabled) return
        closeLogFile()
        try {
            val dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val logFile = File(LOG_DIR, "voicesummon-$dateStr.log")
            if (logFile.exists()) {
                logFile.delete()
            }
        } catch (_: Exception) {
        }
        openLogFile()
    }

    @Synchronized
    fun shutdown() {
        closeLogFile()
    }

    private fun stackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    
    class ProxySLF4JLogger(
        private val delegate: Logger,
        private val loggerName: String
    ) : Logger by delegate {

        private fun logWithFile(level: String, msg: String, throwable: Throwable? = null) {
            val fullMsg = if (throwable != null) {
                "$msg\n${stackTrace(throwable)}"
            } else {
                msg
            }
            writeToFile(level, loggerName, fullMsg)
        }

        override fun info(msg: String) {
            delegate.info(msg)
            logWithFile("INFO", msg)
        }

        override fun info(format: String, arg: Any?) {
            delegate.info(format, arg)
            logWithFile("INFO", formatMessage(format, arrayOf(arg)))
        }

        override fun info(format: String, vararg args: Any?) {
            delegate.info(format, *args)
            logWithFile("INFO", formatMessage(format, args))
        }

        override fun info(format: String, arg1: Any?, arg2: Any?) {
            delegate.info(format, arg1, arg2)
            logWithFile("INFO", formatMessage(format, arrayOf(arg1, arg2)))
        }

        override fun warn(msg: String) {
            delegate.warn(msg)
            logWithFile("WARN", msg)
        }

        override fun warn(format: String, arg: Any?) {
            delegate.warn(format, arg)
            logWithFile("WARN", formatMessage(format, arrayOf(arg)))
        }

        override fun warn(format: String, vararg args: Any?) {
            delegate.warn(format, *args)
            logWithFile("WARN", formatMessage(format, args))
        }

        override fun error(msg: String) {
            delegate.error(msg)
            logWithFile("ERROR", msg)
        }

        override fun error(format: String, arg: Any?) {
            delegate.error(format, arg)
            logWithFile("ERROR", formatMessage(format, arrayOf(arg)))
        }

        override fun error(format: String, vararg args: Any?) {
            delegate.error(format, *args)
            logWithFile("ERROR", formatMessage(format, args))
        }

        override fun error(msg: String, throwable: Throwable) {
            delegate.error(msg, throwable)
            logWithFile("ERROR", msg, throwable)
        }

        override fun debug(msg: String) {
            delegate.debug(msg)
            logWithFile("DEBUG", msg)
        }

        override fun debug(format: String, arg: Any?) {
            delegate.debug(format, arg)
            logWithFile("DEBUG", formatMessage(format, arrayOf(arg)))
        }

        override fun debug(format: String, vararg args: Any?) {
            delegate.debug(format, *args)
            logWithFile("DEBUG", formatMessage(format, args))
        }
    }
}
