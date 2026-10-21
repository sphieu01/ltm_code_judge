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
        return problemRepository.findAll();
    }

    public List<Problem> getAllProblemsForStudent() throws SQLException {
        return problemRepository.findAllForStudent();
    }

    /**
     * Lấy danh sách bài toán theo lớp tín chỉ (hoặc bài tập chung).
     */
    public List<Problem> getProblemsByClass(int classId) throws SQLException {
        if (classId <= 0) {
            return problemRepository.findAll();
        }
        return problemRepository.findByClassId(classId);
    }

    public List<Problem> getProblemsByClassForStudent(int classId) throws SQLException {
        if (classId <= 0) {
            return problemRepository.findAllForStudent();
        }
        return problemRepository.findByClassIdForStudent(classId);
    }

    /**
     * Lấy chi tiết 1 bài toán kèm sample test cases.
     * Dùng cho GET_PROBLEM_DETAIL response.
     */
    public Problem getProblemDetail(int problemId) throws SQLException {
        return getProblemDetail(problemId, false);
    }

    public Problem getProblemDetail(int problemId, boolean includeHiddenTests) throws SQLException {
        Optional<Problem> opt = problemRepository.findById(problemId, includeHiddenTests);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Bài toán id=" + problemId + " không tồn tại");
        }
        return opt.get();
    }

    /**
     * Tạo bài toán mới kèm các test case.
     */
    public Problem createProblem(Problem problem, List<com.grading.server.model.TestCase> testCases) throws SQLException {
        Problem saved = problemRepository.save(problem);
        if (testCases != null && !testCases.isEmpty()) {
            for (com.grading.server.model.TestCase tc : testCases) {
                tc.setProblemId(saved.getId());
                problemRepository.saveTestCase(tc);
            }
        }
        return saved;
    }

    public void setProblemActive(int problemId, boolean active) throws SQLException {
        problemRepository.updateProblemActive(problemId, active);
    }

    public Problem updateProblem(Problem problem, List<com.grading.server.model.TestCase> testCases) throws SQLException {
        if (testCases == null || testCases.isEmpty()) {
            throw new IllegalArgumentException("Bài tập phải có ít nhất một test case");
        }
        return problemRepository.updateWithTestCases(problem, testCases);
    }
}
