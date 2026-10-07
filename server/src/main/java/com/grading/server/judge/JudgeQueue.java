package com.grading.server.judge;

import com.grading.server.judge.model.SubmissionTask;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Hàng đợi thread-safe lưu trữ các bài nộp chờ chấm.
 * Sử dụng cơ chế Producer-Consumer bọc quanh LinkedBlockingQueue.
 */
public class JudgeQueue {

    private final LinkedBlockingQueue<SubmissionTask> queue;

    public JudgeQueue() {
        this.queue = new LinkedBlockingQueue<>();
    }

    public JudgeQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Queue capacity phải lớn hơn 0");
        }
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    /**
     * Đẩy bài nộp vào hàng đợi. Nếu hàng đợi đầy, phương thức sẽ block cho đến khi có chỗ trống.
     */
    public void push(SubmissionTask task) throws InterruptedException {
        if (task == null) {
            throw new IllegalArgumentException("SubmissionTask không được là null");
        }
        queue.put(task);
    }

    /**
     * Lấy bài nộp ra khỏi hàng đợi để chấm. Block cho đến khi có bài mới.
     */
    public SubmissionTask take() throws InterruptedException {
        return queue.take();
    }

    /**
     * Lấy bài nộp ra khỏi hàng đợi với thời gian chờ tối đa.
     */
    public SubmissionTask poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /**
     * Số lượng bài nộp đang chờ trong hàng đợi.
     */
    public int size() {
        return queue.size();
    }

    /**
     * Kiểm tra hàng đợi có rỗng hay không.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Xóa sạch các bài nộp còn trong hàng đợi.
     */
    public void clear() {
        queue.clear();
    }
}
