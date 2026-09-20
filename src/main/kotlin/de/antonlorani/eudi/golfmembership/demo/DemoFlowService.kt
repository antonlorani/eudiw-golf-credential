package de.antonlorani.eudi.golfmembership.demo

import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Service
class DemoFlowService {

    private val maxSessions = 1000
    private val sessions = ConcurrentHashMap<String, DemoSession>()
    private val insertionOrder = ConcurrentLinkedQueue<String>()

    fun createSession(): DemoSession {
        val id = UUID.randomUUID().toString().substring(0, 8)
        val session = DemoSession(id = id, step = DemoStep.BOOKING)
        sessions[id] = session
        insertionOrder.add(id)
        evict()
        return session
    }

    private fun evict() {
        while (sessions.size > maxSessions) {
            val oldest = insertionOrder.poll() ?: break
            sessions.remove(oldest)
        }
    }

    fun getSession(id: String): DemoSession? = sessions[id]

    fun advance(id: String, selection: String?): DemoSession? {
        return sessions.computeIfPresent(id) { _, session ->
            when (session.step) {
                DemoStep.BOOKING -> session.copy(step = DemoStep.VERIFY_HCP, selection = selection)
                DemoStep.VERIFY_HCP -> session.copy(step = DemoStep.SUCCESS)
                else -> session
            }
        }
    }
}
