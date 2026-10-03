package com.grading.server.service;

import com.grading.server.model.Problem;
import com.grading.server.repository.ProblemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Service xử lý logic nghiệp vụ cho bài toán (Problem).
 */
public class ProblemService {

    private static final Logger logger = LoggerFactory.getLogger(ProblemService.class);

    private final ProblemRepository problemRepository;

    public ProblemService(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    /**
     * Lấy danh sách tất cả bài toán (không kèm test cases).
     * Dùng cho GET_PROBLEMS response.
     */
    public List<Problem> getAllProblems() throws SQLException {
        List<Problem> problems = problemRepository.findAll();
        logger.debug("Fetched {} problems", problems.size());
        return problems;
    }

    /**
     * Lấy chi tiết 1 bài toán kèm sample test cases.
     * Dùng cho GET_PROBLEM_DETAIL response.
     *
     * @throws IllegalArgumentException nếu không tìm thấy bài
     */
    public Problem getProblemDetail(int problemId) throws SQLException {
        Optional<Problem> opt = problemRepository.findById(problemId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Bài toán id=" + problemId + " không tồn tại");
        }
        return opt.get();
    }
}
