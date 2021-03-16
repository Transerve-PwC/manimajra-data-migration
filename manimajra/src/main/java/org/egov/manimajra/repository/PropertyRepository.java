package org.egov.manimajra.repository;

import org.egov.manimajra.entities.Property;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, String>{

	public Property getPropertyByFileNumber(String fileNumber);
}
