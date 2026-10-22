package com.grading.server.judge;

import com.grading.server.judge.model.JudgeResult;
import com.grading.server.judge.model.SubmissionTask;

/**
 * Functional interface lắng nghe sự kiện khi một bài nộp được chấm xong.
 */
@FunctionalInterface
public interface OnJudgeCompletedListener {

    /**
     * Kích hoạt khi quá trình chấm bài hoàn tất.
     *
     * @param task bài nộp vừa chấm xong
     * @param result kết quả chấm bài
     */
    void onCompleted(SubmissionTask task, JudgeResult result);
}
