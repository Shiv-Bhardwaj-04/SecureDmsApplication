package com.dms.secure_dms.repository;

import com.dms.secure_dms.entity.User;
import com.dms.secure_dms.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    List<User> findByRoleOrderByFullNameAsc(Role role);
}
