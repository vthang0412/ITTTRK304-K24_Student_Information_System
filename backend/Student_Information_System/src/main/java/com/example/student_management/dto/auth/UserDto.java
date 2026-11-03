package com.example.student_management.dto.auth;



import com.example.student_management.entity.auth.AccountStatus;
import com.example.student_management.entity.auth.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private Role role;
    private AccountStatus status;
}
