package com.example.student_management.service.impl;



import com.example.student_management.entity.Student;
import com.example.student_management.exception.BadRequestException;
import com.example.student_management.exception.ConflictException;
import com.example.student_management.exception.NotFoundException;
import com.example.student_management.repository.StudentRepository;
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
        if (student.getStudentCode() == null || student.getStudentCode().isBlank()) {
            throw new BadRequestException("Student code is required");
        }
        if (studentRepository.existsByStudentCode(student.getStudentCode())) {
            throw new ConflictException("Student code already exists");
        }
        if (student.getEmail() != null && studentRepository.existsByEmail(student.getEmail())) {
            throw new ConflictException("Email already exists");
        }
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
        existing.setStatus(student.getStatus());
        return studentRepository.save(existing);
    }
}
