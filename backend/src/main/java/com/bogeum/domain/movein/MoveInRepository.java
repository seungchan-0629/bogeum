package com.bogeum.domain.movein;

import org.springframework.data.jpa.repository.JpaRepository;
import com.bogeum.domain.movein.model.MoveInDefectPhoto;

/** 계약에 등록한 입주 하자 사진 메타데이터를 저장한다. */
public interface MoveInRepository extends JpaRepository<MoveInDefectPhoto, Long> {
}
