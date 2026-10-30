package com.example.student_management.service.impl;

import com.example.student_management.entity.AccountStatus;
import com.example.student_management.entity.Student;
import com.example.student_management.entity.User;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.StudentRepository;
import com.example.student_management.repository.UserRepository;
import com.example.student_management.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class StudentServiceImpl implements StudentService {
    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    @Override
    public Optional<Student> findById(Long id) {
        return studentRepository.findById(id);
    }

    @Override
    @Transactional
    public Student create(Student student) {
        String studentCode = student.getStudentCode() == null ? "" : student.getStudentCode().trim();
        if (studentCode.isBlank()) {
            studentCode = generateUniqueStudentCode();
        }

        String email = student.getEmail() == null ? "" : student.getEmail().trim();
        if (studentRepository.existsByStudentCode(studentCode)) {
            throw new ConflictException("Student code already exists");
        }
        if (email.isBlank()) {
            throw new BadRequestException("Email is required");
        }
        if (userRepository.findByEmail(email).isPresent()
                || userRepository.findByEmail(email.toLowerCase()).isPresent()
                || studentRepository.findAll().stream().anyMatch(existing -> existing.getEmail() != null
                        && existing.getEmail().equalsIgnoreCase(email))
                || studentRepository.findAll().stream().anyMatch(existing -> existing.getEmail() != null
                        && existing.getEmail().equalsIgnoreCase(email.toLowerCase()))) {
            throw new ConflictException("Email already exists");
        }

        student.setStudentCode(studentCode);
        student.setEmail(email);
        return studentRepository.save(student);
    }

    @Override
    @Transactional
    public Student update(Long id, Student student) {
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Student not found"));
        existing.setFullName(student.getFullName());
        existing.setEmail(student.getEmail());
        existing.setDepartment(student.getDepartment());
        if (student.getStatus() != null) {
            existing.setStatus(student.getStatus());
        }

        if (existing.getUser() != null) {
            if (student.getStatus() != null) {
                existing.getUser().setStatus(student.getStatus());
            }
            if (student.getFullName() != null) {
                existing.getUser().setFullName(student.getFullName());
            }
            if (student.getEmail() != null) {
                existing.getUser().setEmail(student.getEmail());
            }
            userRepository.save(existing.getUser());
        }

        return studentRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Student not found"));

        if (existing.getStatus() != AccountStatus.INACTIVE) {
            throw new BadRequestException("Only inactive accounts can be deleted.");
        }

        if (existing.getUser() != null) {
            User user = existing.getUser();
            if (user.getRole() == com.example.student_management.entity.Role.ADMIN) {
                throw new BadRequestException("Admin accounts cannot be deleted.");
            }
            if (user.getStatus() != AccountStatus.INACTIVE) {
                throw new BadRequestException("Only inactive accounts can be deleted.");
            }
            if (user.getStudent() != null && user.getStudent().getId() != null && !user.getStudent().getId().equals(existing.getId())) {
                throw new BadRequestException("Account is linked to multiple profiles and cannot be deleted automatically.");
            }
            if (user.getLecturer() != null) {
                throw new BadRequestException("Account is linked to a lecturer profile and cannot be deleted here.");
            }
        }

        studentRepository.delete(existing);

        if (existing.getUser() != null) {
            userRepository.delete(existing.getUser());
        }
    }

    private String generateUniqueStudentCode() {
        String code;
        do {
            int random = (int) (Math.random() * 900000) + 100000;
            code = "SV" + random;
        } while (studentRepository.existsByStudentCode(code));
        return code;
    }
}
