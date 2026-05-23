package com.example.yingzaofashiapi.Repository;

import com.example.yingzaofashiapi.entity.Heritage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface HeritageRepository extends JpaRepository<Heritage, Long> {

    List<Heritage> findByPeriod(String period);

    @Query("SELECT h FROM Heritage h WHERE h.period LIKE CONCAT(:period, '%')")
    List<Heritage> findByPeriodStartingWith(@Param("period") String period);

    List<Heritage> findByPeriodCodeBetween(int startCode, int endCode);

    @Query(value = "SELECT * FROM heritage WHERE " +
            "period REGEXP '19[5-9][0-9]年|20[0-9]{2}年' " +
            "OR period LIKE '%中华人民共和国%' " +
            "OR period LIKE '%共和国%' " +
            "OR period LIKE '%1949年后%' " +
            "OR period LIKE '%唐至中华人民共和国%' " +
            "OR period LIKE '%宋至中华人民共和国%' " +
            "OR period LIKE '%清至中华人民共和国%' " +
            "OR period LIKE '%1949年%'",
            nativeQuery = true)
    List<Heritage> findModernChinaHeritage();

    // 修正：使用 class_no 而不是 classification_number
    @Query(value = "SELECT class_no, name, period, address, type, batch, province, city, county " +
            "FROM heritage WHERE province = :province", nativeQuery = true)
    List<Object[]> findDetailByProvince(@Param("province") String province);
}