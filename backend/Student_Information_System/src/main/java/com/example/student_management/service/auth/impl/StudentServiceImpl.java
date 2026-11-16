package com.example.student_management.service.auth.impl;

import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import com.example.student_management.entity.auth.Student;
import com.example.student_management.entity.auth.User;
import com.example.student_management.dto.auth.StudentAccountCreateRequest;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.auth.StudentRepository;
import com.example.student_management.repository.auth.UserRepository;
import com.example.student_management.service.auth.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.security.SecureRandom;

@Service
public class StudentServiceImpl implements StudentService {
    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
    public Student createAccount(StudentAccountCreateRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim();
        String fullName = request.getFullName().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already exists");
        }
        if (userRepository.existsByEmail(email) || studentRepository.existsByEmail(email)) {
            throw new ConflictException("Email already exists");
        }

        AccountStatus status = request.getStatus() == null ? AccountStatus.ACTIVE : request.getStatus();
        User user = User.builder()
                .username(username)
                .email(email)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.STUDENT)
                .status(status)
                .build();
        user = userRepository.save(user);

        Student student = Student.builder()
                .studentCode(generateUniqueStudentCode())
                .fullName(fullName)
                .email(email)
                .department(request.getDepartment() == null ? "" : request.getDepartment().trim())
                .status(status)
                .user(user)
                .build();
        user.setStudent(student);
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
            if (user.getRole() == Role.ADMIN) {
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
            int random = SECURE_RANDOM.nextInt(900000) + 100000;
            code = "SV" + random;
        } while (studentRepository.existsByStudentCode(code));
        return code;
    }
}
