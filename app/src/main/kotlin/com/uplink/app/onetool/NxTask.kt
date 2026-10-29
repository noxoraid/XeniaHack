package com.uplink.app

object NxTask {
    fun await(kind: TaskKind, title: String, stopped: () -> Boolean, work: (TaskRun) -> Unit): TaskRun {
        val run = TaskBoard.start(kind, title, true, work)
        while (run.state == TaskState.Running) {
            if (stopped()) run.stop = true
            Thread.sleep(100)
        }
        when (run.state) {
            TaskState.Stopped -> throw TaskStopped()
            TaskState.Failed -> throw Exception(run.error ?: tr("Command failed"))
            else -> Unit
        }
        return run
    }
}
