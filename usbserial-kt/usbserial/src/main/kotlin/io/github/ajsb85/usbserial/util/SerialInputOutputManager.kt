package io.github.ajsb85.usbserial.util

import android.os.Process
import io.github.ajsb85.usbserial.UsbSerialPort
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Services a [UsbSerialPort] on background threads: delivers incoming data to a [Listener] and
 * sends queued outgoing data. Useful for live serial monitoring (e.g. watching ESP boot logs)
 * alongside synchronous flashing.
 */
class SerialInputOutputManager(
    private val port: UsbSerialPort,
    @Volatile var listener: Listener? = null,
) {
    enum class State { STOPPED, STARTING, RUNNING, STOPPING }

    interface Listener {
        /** New incoming bytes. */
        fun onNewData(data: ByteArray)

        /** A read or write thread aborted with an error. */
        fun onRunError(e: Exception)
    }

    @Volatile var readTimeout: Int = 0

    @Volatile var writeTimeout: Int = 0
    var readBufferSize: Int = port.readEndpoint?.maxPacketSize ?: 4096
    var threadPriority: Int = Process.THREAD_PRIORITY_URGENT_AUDIO

    private val state = AtomicReference(State.STOPPED)
    private val writeLock = ReentrantLock()
    private val writable = writeLock.newCondition()
    private val writeBuffer = ByteArrayOutputStream()
    private var startLatch = CountDownLatch(2)

    val currentState: State get() = state.get()

    /** Queue [data] to be written asynchronously by the write thread. */
    fun writeAsync(data: ByteArray) {
        writeLock.withLock {
            writeBuffer.write(data)
            writable.signalAll()
        }
    }

    /** Start the read and write threads. */
    fun start() {
        check(state.compareAndSet(State.STOPPED, State.STARTING)) { "already started" }
        startLatch = CountDownLatch(2)
        Thread(::runRead, "${javaClass.simpleName}_read").start()
        Thread(::runWrite, "${javaClass.simpleName}_write").start()
        startLatch.await()
        state.set(State.RUNNING)
    }

    /**
     * Request the threads to stop. When [readTimeout] is 0 (blocking reads), also call
     * [UsbSerialPort.close] to interrupt the in-flight read.
     */
    fun stop() {
        if (state.compareAndSet(State.RUNNING, State.STOPPING)) {
            writeLock.withLock { writable.signalAll() }
        }
    }

    private fun running(): Boolean {
        val s = state.get()
        return (s == State.RUNNING || s == State.STARTING) && !Thread.currentThread().isInterrupted
    }

    private fun applyPriority() {
        if (threadPriority != Process.THREAD_PRIORITY_DEFAULT) Process.setThreadPriority(threadPriority)
    }

    private fun notifyError(e: Throwable) {
        listener?.let { l ->
            runCatching { l.onRunError(if (e is Exception) e else Exception(e)) }
        }
    }

    private fun runRead() {
        try {
            applyPriority()
            startLatch.countDown()
            val buffer = ByteArray(readBufferSize)
            do {
                val len = port.read(buffer, readTimeout)
                if (len > 0) listener?.onNewData(buffer.copyOf(len))
            } while (running())
        } catch (e: Throwable) {
            if (!Thread.currentThread().isInterrupted && port.isOpen) notifyError(e)
        } finally {
            if (state.compareAndSet(State.RUNNING, State.STOPPING)) {
                writeLock.withLock { writable.signalAll() }
            } else {
                state.compareAndSet(State.STOPPING, State.STOPPED)
            }
        }
    }

    private fun runWrite() {
        try {
            applyPriority()
            startLatch.countDown()
            do {
                val chunk: ByteArray? = writeLock.withLock {
                    if (writeBuffer.size() > 0) {
                        writeBuffer.toByteArray().also {
                            writeBuffer.reset()
                            writable.signalAll()
                        }
                    } else {
                        writable.await()
                        null
                    }
                }
                chunk?.let { port.write(it, writeTimeout) }
            } while (running())
        } catch (e: Throwable) {
            if (!Thread.currentThread().isInterrupted && port.isOpen) notifyError(e)
        } finally {
            if (!state.compareAndSet(State.RUNNING, State.STOPPING)) {
                state.compareAndSet(State.STOPPING, State.STOPPED)
            }
        }
    }
}
