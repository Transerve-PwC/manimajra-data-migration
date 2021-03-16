package org.egov.manimajra.entities;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
@Entity
@Table(name = "cs_ep_property_details_v1")
public class PropertyDetails extends AuditDetails {

	@Id
	@GeneratedValue(generator = "UUID")
	@GenericGenerator(
			name = "UUID",
			strategy = "org.hibernate.id.UUIDGenerator"
	)
	@Column(name = "id")
	private String id;

	@Column(name = "tenantid")
	private String tenantId;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "property_id")
	private Property property;

	@Column(name = "house_number")
	private String houseNumber;

	@Column(name = "street")
	private String street;

	@Column(name = "mohalla")
	private String mohalla;

	@Column(name = "area_sqft")
	private int areaSqft;

	@Column(name = "property_type")
	private String propertyType;

	@Column(name = "last_noc_date")
	private Long lastNocDate;

	@Column(name = "service_category")
	private String serviceCategory;

	@Column(name = "mm_demand_start_year")
	private int mmDemandStartYear;
	
	@Column(name = "mm_demand_start_month")
	private int mmDemandStartMonth;

	@OneToMany(
			cascade = CascadeType.ALL,
			mappedBy = "propertyDetails"
			)
	private Set<Owner> owners = new HashSet<Owner>();

	@Column(name = "branch_type")
	private String branchType;

	@OneToMany(
			cascade = CascadeType.ALL,
			mappedBy = "propertyDetails"
			)
	private Set<CourtCase> courtCases = new HashSet<CourtCase>();

}
