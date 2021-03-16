package org.egov.manimajra.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.compress.archivers.dump.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.Styles;
import org.apache.poi.xssf.model.StylesTable;
import org.egov.manimajra.entities.CourtCase;
import org.egov.manimajra.entities.Document;
import org.egov.manimajra.entities.Owner;
import org.egov.manimajra.entities.OwnerDetails;
import org.egov.manimajra.entities.Property;
import org.egov.manimajra.entities.PropertyDetails;
import org.egov.manimajra.model.PropertyResponse;
import org.egov.manimajra.repository.PropertyRepository;
import org.egov.manimajra.service.StreamingSheetContentsHandler.StreamingRowProcessor;
import org.egov.manimajra.util.FileStoreUtils;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReadExcelServiceImpl implements ReadExcelService {

	private static final String SYSTEM = "System";
	private static final String TENANTID = "ch.chandigarh";
	private static final String APPROVE = "APPROVE";
	private static final String ES_PM_MM_APPROVED = "ES_PM_MM_APPROVED";
	private static final String ES_DRAFTED = "ES_DRAFTED";
	private static final String PROPERTY_MASTER = "PROPERTY_MASTER";

	@Autowired
	private PropertyRepository propertyRepository;

	@Autowired
	private FileStoreUtils fileStoreUtils;

	@Value("${file.location}")
	private String fileLocation;

	@Override
	public PropertyResponse getDataFromExcel(File file, int sheetIndex) {
		try {
			OPCPackage opcPackage = OPCPackage.open(file);
			return this.process(opcPackage, sheetIndex);
		} catch (IOException | OpenXML4JException | SAXException e) {
			log.error("Error while parsing Excel", e);
			throw new CustomException("PARSE_ERROR", "Could not parse excel. Error is " + e.getMessage());
		}
	}

	@Override
	public PropertyResponse getDataFromExcelforOwner(File file, int sheetIndex) {
		try {
			OPCPackage opcPackage = OPCPackage.open(file);
			return this.processOwner(opcPackage, sheetIndex);
		} catch (IOException | OpenXML4JException | SAXException e) {
			log.error("Error while parsing Excel", e);
			throw new CustomException("PARSE_ERROR", "Could not parse excel. Error is " + e.getMessage());
		}
	}

	@Override
	public PropertyResponse getDataFromExcelforPreviousOwner(File file, int sheetIndex) {
		try {
			OPCPackage opcPackage = OPCPackage.open(file);
			return this.processPreviousOwner(opcPackage, sheetIndex);
		} catch (IOException | OpenXML4JException | SAXException e) {
			log.error("Error while parsing Excel", e);
			throw new CustomException("PARSE_ERROR", "Could not parse excel. Error is " + e.getMessage());
		}
	}

	@Override
	public PropertyResponse getDataFromExcelforCourtCase(File file, int sheetIndex) {
		try {
			OPCPackage opcPackage = OPCPackage.open(file);
			return this.processCourtCase(opcPackage, sheetIndex);
		} catch (IOException | OpenXML4JException | SAXException e) {
			log.error("Error while parsing Excel", e);
			throw new CustomException("PARSE_ERROR", "Could not parse excel. Error is " + e.getMessage());
		}
	}

	@Override
	public PropertyResponse getDataFromExcelforDoc(File file, int sheetIndex) {
		try {
			OPCPackage opcPackage = OPCPackage.open(file);
			return this.processDoc(opcPackage, sheetIndex);
		} catch (IOException | OpenXML4JException | SAXException e) {
			log.error("Error while parsing Excel", e);
			throw new CustomException("PARSE_ERROR", "Could not parse excel. Error is " + e.getMessage());
		}
	}

	private PropertyResponse process(OPCPackage xlsxPackage, int sheetNo)
			throws IOException, OpenXML4JException, SAXException, CustomException {
		ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(xlsxPackage);
		XSSFReader xssfReader = new XSSFReader(xlsxPackage);
		StylesTable styles = xssfReader.getStylesTable();
		XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
		int index = 0;
		while (iter.hasNext()) {
			try (InputStream stream = iter.next()) {

				if (index == sheetNo) {
					SheetContentsProcessor processor = new SheetContentsProcessor();
					processSheet(styles, strings, new StreamingSheetContentsHandler(processor), stream);
					if (!processor.propertyList.isEmpty()) {
						return saveProperties(processor.propertyList, processor.skippedFileNos);
					} else {
						PropertyResponse propertyResponse = PropertyResponse.builder()
								.skippedFileNos(processor.skippedFileNos)
								.build();
						return propertyResponse;
					}
				}
				index++;
			}
		}
		throw new CustomException("PARSE_ERROR", "Could not process sheet no " + sheetNo);
	}

	private PropertyResponse processOwner(OPCPackage xlsxPackage, int sheetNo)
			throws IOException, OpenXML4JException, SAXException, CustomException {
		ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(xlsxPackage);
		XSSFReader xssfReader = new XSSFReader(xlsxPackage);
		StylesTable styles = xssfReader.getStylesTable();
		XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
		int index = 0;
		while (iter.hasNext()) {
			try (InputStream stream = iter.next()) {

				if (index == sheetNo) {
					SheetContentsProcessorOwner processorOwner = new SheetContentsProcessorOwner();
					processSheet(styles, strings, new StreamingSheetContentsHandler(processorOwner), stream);
					if (!processorOwner.propertyList.isEmpty()) {
						PropertyResponse propertyResponse = PropertyResponse.builder()
								.generatedCount(processorOwner.propertyList.size())
								.skippedFileNos(processorOwner.skippedFileNos).build();
						return propertyResponse;
					} else {
						PropertyResponse propertyResponse = PropertyResponse.builder()
								.skippedFileNos(processorOwner.skippedFileNos)
								.build();
						return propertyResponse;
					}
				}
				index++;
			}
		}
		throw new CustomException("PARSE_ERROR", "Could not process sheet no " + sheetNo);
	}

	private PropertyResponse processPreviousOwner(OPCPackage xlsxPackage, int sheetNo)
			throws IOException, OpenXML4JException, SAXException, CustomException {
		ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(xlsxPackage);
		XSSFReader xssfReader = new XSSFReader(xlsxPackage);
		StylesTable styles = xssfReader.getStylesTable();
		XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
		int index = 0;
		while (iter.hasNext()) {
			try (InputStream stream = iter.next()) {

				if (index == sheetNo) {
					SheetContentsProcessorPreviousOwner processorPreviousOwner = new SheetContentsProcessorPreviousOwner();
					processSheet(styles, strings, new StreamingSheetContentsHandler(processorPreviousOwner), stream);
					if (!processorPreviousOwner.propertyList.isEmpty()) {
						PropertyResponse propertyResponse = PropertyResponse.builder()
								.generatedCount(processorPreviousOwner.propertyList.size())
								.skippedFileNos(processorPreviousOwner.skippedFileNos).build();
						return propertyResponse;
					} else {
						PropertyResponse propertyResponse = PropertyResponse.builder()
								.skippedFileNos(processorPreviousOwner.skippedFileNos)
								.build();
						return propertyResponse;
					}
				}
				index++;
			}
		}
		throw new CustomException("PARSE_ERROR", "Could not process sheet no " + sheetNo);
	}

	private PropertyResponse processCourtCase(OPCPackage xlsxPackage, int sheetNo)
			throws IOException, OpenXML4JException, SAXException, CustomException {
		ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(xlsxPackage);
		XSSFReader xssfReader = new XSSFReader(xlsxPackage);
		StylesTable styles = xssfReader.getStylesTable();
		XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
		int index = 0;
		while (iter.hasNext()) {
			try (InputStream stream = iter.next()) {

				if (index == sheetNo) {
					SheetContentsProcessorCourtCase processorCourtCase = new SheetContentsProcessorCourtCase();
					processSheet(styles, strings, new StreamingSheetContentsHandler(processorCourtCase), stream);
					if (!processorCourtCase.propertyList.isEmpty()) {
						PropertyResponse propertyResponse = PropertyResponse.builder()
								.generatedCount(processorCourtCase.propertyList.size())
								.skippedFileNos(processorCourtCase.skippedFileNos).build();
						return propertyResponse;
					} else {
						PropertyResponse propertyResponse = PropertyResponse.builder()
								.skippedFileNos(processorCourtCase.skippedFileNos)
								.build();
						return propertyResponse;
					}
				}
				index++;
			}
		}
		throw new CustomException("PARSE_ERROR", "Could not process sheet no " + sheetNo);
	}

	private PropertyResponse processDoc(OPCPackage xlsxPackage, int sheetNo)
			throws IOException, OpenXML4JException, SAXException, CustomException {
		ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(xlsxPackage);
		XSSFReader xssfReader = new XSSFReader(xlsxPackage);
		StylesTable styles = xssfReader.getStylesTable();
		XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
		int index = 0;
		while (iter.hasNext()) {
			try (InputStream stream = iter.next()) {

				if (index == sheetNo) {
					SheetContentsProcessorDoc processorDoc = new SheetContentsProcessorDoc();
					processSheet(styles, strings, new StreamingSheetContentsHandler(processorDoc), stream);
					if (!processorDoc.propertyList.isEmpty()) {
						PropertyResponse propertyResponse = PropertyResponse.builder()
								.generatedCount(processorDoc.propertyList.size())
								.skippedFileNos(processorDoc.skippedFileNos).build();
						return propertyResponse;
					} else {
						PropertyResponse propertyResponse = PropertyResponse.builder()
								.skippedFileNos(processorDoc.skippedFileNos)
								.build();
						return propertyResponse;
					}
				}
				index++;
			}
		}
		throw new CustomException("PARSE_ERROR", "Could not process sheet no " + sheetNo);
	}

	private void processSheet(Styles styles, SharedStrings strings, SheetContentsHandler sheetHandler,
			InputStream sheetInputStream) throws IOException, SAXException {
		DataFormatter formatter = new DataFormatter();
		InputSource sheetSource = new InputSource(sheetInputStream);
		try {
			SAXParserFactory saxFactory = SAXParserFactory.newInstance();
			saxFactory.setNamespaceAware(false);
			SAXParser saxParser = saxFactory.newSAXParser();
			XMLReader sheetParser = saxParser.getXMLReader();
			ContentHandler handler = new MyXSSFSheetXMLHandler(styles, null, strings, sheetHandler, formatter, false);
			sheetParser.setContentHandler(handler);
			sheetParser.parse(sheetSource);
		} catch (ParserConfigurationException e) {
			throw new RuntimeException("SAX parser appears to be broken - " + e.getMessage());
		}
	}

	protected Object getValueFromCell(Row row, int cellNo, Row.MissingCellPolicy cellPolicy) {
		Cell cell1 = row.getCell(cellNo, cellPolicy);
		Object objValue = "";
		switch (cell1.getCellType()) {
		case BLANK:
			objValue = "";
			break;
		case STRING:
			objValue = cell1.getStringCellValue();
			break;
		case NUMERIC:
			try {
				if (DateUtil.isCellDateFormatted(cell1)) {
					objValue = cell1.getDateCellValue().getTime();
				} else {
					throw new InvalidFormatException();
				}
			} catch (Exception ex1) {
				try {
					objValue = cell1.getNumericCellValue();
				} catch (Exception ex2) {
					objValue = 0.0;
				}
			}

			break;
		case FORMULA:
			objValue = cell1.getNumericCellValue();
			break;

		default:
			objValue = "";
		}
		return objValue;
	}

	protected long convertStrDatetoLong(String dateStr) {
		try {
			SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy");
			Date d = f.parse(dateStr);
			return d.getTime();
		} catch (Exception e) {
			log.error("Date parsing issue occur :" + e.getMessage());
		}
		return 0;
	}

	private class SheetContentsProcessor implements StreamingRowProcessor {

		List<Property> propertyList = new ArrayList<>();
		Set<String> skippedFileNos = new HashSet<>();

		@Override
		public void processRow(Row currentRow) {
			if (currentRow.getRowNum() >= 7) {
				if (currentRow.getCell(1) != null) {
					String firstCell = String
							.valueOf(getValueFromCell(currentRow, 1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))
							.trim();
					if (isNumeric(firstCell)) {
						firstCell = String.valueOf(Double.valueOf(firstCell).intValue());
					}
					Property propertyDb = propertyRepository
								.getPropertyByFileNumber(firstCell);
					
					if (propertyDb == null) {

						int i = 2;
						List<String> excelValues = new ArrayList<>();
						while (i <= 14) {
							excelValues.add(String
									.valueOf(
											getValueFromCell(currentRow, i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))
									.trim());
							i++;
						}
						if (isNumeric(excelValues.get(3)) && isNumeric(excelValues.get(11)) && isNumeric(excelValues.get(12))) {

								PropertyDetails propertyDetails = PropertyDetails.builder().tenantId(TENANTID)
									.houseNumber(excelValues.get(0))
									.mohalla(excelValues.get(1))
									.street(excelValues.get(2))
									.areaSqft(Double.valueOf(excelValues.get(3)).intValue())
									.propertyType(excelValues.get(4))
									.branchType("MANI_MAJRA")
									.lastNocDate(convertStrDatetoLong(excelValues.get(9)))
									.serviceCategory(excelValues.get(10))
									.mmDemandStartMonth(Double.valueOf(excelValues.get(11)).intValue())
									.mmDemandStartYear(Double.valueOf(excelValues.get(12)).intValue())
									.build();

							Property property = Property.builder().tenantId(TENANTID).action("").state(ES_DRAFTED)
									.propertyMasterOrAllotmentOfSite(PROPERTY_MASTER)
									.fileNumber(firstCell)
									.category(excelValues.get(5)).subCategory(excelValues.get(6))
									.siteNumber(String.valueOf(Math.round(Float.parseFloat(excelValues.get(7)))))
									.sectorNumber(excelValues.get(8))
									.propertyDetails(propertyDetails).build();

							propertyDetails.setProperty(property);
							propertyDetails.setCreatedBy(SYSTEM);
							property.setCreatedBy(SYSTEM);
							propertyList.add(property);
						} else {
								skippedFileNos.add(firstCell);
								log.error("We are skipping uploading property for file number: " + firstCell
										+ " because of incorrect data.");

						}
					} else {
							skippedFileNos.add(firstCell);
							log.error("We are skipping uploading property for file number: " + firstCell
									+ " as it already exists.");

					}
				}
			}

		}
	}

	private class SheetContentsProcessorOwner implements StreamingRowProcessor {

		Set<String> skippedFileNos = new HashSet<>();
		List<Property> propertyList = new ArrayList<>();

		@Override
		public void processRow(Row currentRow) {
			if (currentRow.getRowNum() >= 7) {
				if (currentRow.getCell(1) != null) {
					String firstCell = String
							.valueOf(getValueFromCell(currentRow, 1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))
							.trim();
					if (isNumeric(firstCell)) {
						firstCell = String.valueOf(Double.valueOf(firstCell).intValue());
					}
					Property propertyDb = propertyRepository
								.getPropertyByFileNumber(firstCell);
					
					if (propertyDb != null) {
						int i = 2;
						List<String> excelValues = new ArrayList<>();
						while (i <= 8) {
							excelValues.add(String
									.valueOf(
											getValueFromCell(currentRow, i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))
									.trim());
							i++;
						}
						if (isNumeric(excelValues.get(4).substring(1, excelValues.get(4).length() - 1))
								&& isNumeric(excelValues.get(5))) {
							OwnerDetails ownerDetails = OwnerDetails.builder().tenantId(TENANTID)
									.ownerName(excelValues.get(0)).guardianName(excelValues.get(1))
									.guardianRelation(excelValues.get(2))
									.address(excelValues.get(3))
									.mobileNumber(excelValues.get(4).substring(1, excelValues.get(4).length() - 1))
									.possesionDate(convertStrDatetoLong(excelValues.get(6)))
									.isCurrentOwner(Boolean.valueOf("true")).build();
							Owner owner = Owner.builder().tenantId(TENANTID).share(Double.valueOf(excelValues.get(5)))
									.build();
							owner.setCreatedBy(SYSTEM);
							ownerDetails.setOwner(owner);
							ownerDetails.setCreatedBy(SYSTEM);
							owner.setOwnerDetails(ownerDetails);
							PropertyDetails propertyDetails = propertyDb.getPropertyDetails();
							Set<Owner> ownerList = new HashSet<>();
							ownerList.add(owner);
							propertyDetails.setOwners(ownerList);
							owner.setPropertyDetails(propertyDetails);
							propertyDb.setPropertyDetails(propertyDetails);
							propertyRepository.save(propertyDb);
							propertyList.add(propertyDb);
						} else {
								skippedFileNos.add(firstCell);
								log.error("We are skipping uploading property for file number: " + firstCell
										+ " because of incorrect data.");
							
						}
					} else {
							skippedFileNos.add(firstCell);
							log.error("We are skipping uploading owner details for property with file number: " + firstCell
									+ " as it does not exists.");

						
					}
				}
			}

		}

	}

	private class SheetContentsProcessorPreviousOwner implements StreamingRowProcessor {

		Set<String> skippedFileNos = new HashSet<>();
		List<Property> propertyList = new ArrayList<>();

		@Override
		public void processRow(Row currentRow) {
			if (currentRow.getRowNum() >= 7) {
				if (currentRow.getCell(1) != null) {
					String firstCell = String
							.valueOf(getValueFromCell(currentRow, 1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))
							.trim();
					if (isNumeric(firstCell)) {
						firstCell = String.valueOf(Double.valueOf(firstCell).intValue());
					}
					Property propertyDb = propertyRepository
								.getPropertyByFileNumber(firstCell);
					
					if (propertyDb != null) {
						int i = 2;
						List<String> excelValues = new ArrayList<>();
						while (i <= 13) {
							excelValues.add(String
									.valueOf(
											getValueFromCell(currentRow, i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))
									.trim());
							i++;
						}
						if (isNumeric(excelValues.get(6).substring(1, excelValues.get(6).length() - 1))
								&& isNumeric(excelValues.get(10))) {
							OwnerDetails ownerDetails = OwnerDetails.builder().tenantId(TENANTID)
									.isPreviousOwnerRequired(Boolean.valueOf(excelValues.get(0)))
									.isCurrentOwner(Boolean.valueOf("false"))
									.ownerName(excelValues.get(1)).guardianName(excelValues.get(2))
									.guardianRelation(excelValues.get(3)).dob(convertStrDatetoLong(excelValues.get(4)))
									.address(excelValues.get(5))
									.mobileNumber(excelValues.get(6).substring(1, excelValues.get(6).length() - 1))
									.sellerName(excelValues.get(7)).sellerGuardianName(excelValues.get(8))
									.sellerRelation(excelValues.get(9)).modeOfTransfer(excelValues.get(11)).build();
							Owner owner = Owner.builder().tenantId(TENANTID).ownerDetails(ownerDetails)
									.share(Double.valueOf(excelValues.get(10))).build();
							owner.setCreatedBy(SYSTEM);
							ownerDetails.setOwner(owner);
							ownerDetails.setCreatedBy(SYSTEM);
							PropertyDetails propertyDetails = propertyDb.getPropertyDetails();
							Set<Owner> ownerList = new HashSet<>();
							ownerList.add(owner);
							propertyDetails.setOwners(ownerList);
							owner.setPropertyDetails(propertyDetails);
							propertyDb.setPropertyDetails(propertyDetails);
							propertyRepository.save(propertyDb);
							propertyList.add(propertyDb);
						} else {
								skippedFileNos.add(firstCell);
								log.error("We are skipping uploading property for file number: " + firstCell
										+ " because of incorrect data.");
							
						}
					} else {
							skippedFileNos.add(firstCell);
							log.error("We are skipping uploading property for file number: " + firstCell
									+ " as it does not exists.");
						
					}
				}
			}
		}

	}

	private class SheetContentsProcessorCourtCase implements StreamingRowProcessor {

		Set<String> skippedFileNos = new HashSet<>();
		List<Property> propertyList = new ArrayList<>();

		@Override
		public void processRow(Row currentRow) {
			if (currentRow.getRowNum() >= 7) {
				if (currentRow.getCell(1) != null) {
					String firstCell = String
							.valueOf(getValueFromCell(currentRow, 1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))
							.trim();
					if (isNumeric(firstCell)) {
						firstCell = String.valueOf(Double.valueOf(firstCell).intValue());
					}
					Property propertyDb = propertyRepository
								.getPropertyByFileNumber(firstCell);
				
					if (propertyDb != null) {
						int i = 2;
						List<String> excelValues = new ArrayList<>();
						while (i <= 8) {
							excelValues.add(String
									.valueOf(
											getValueFromCell(currentRow, i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))
									.trim());
							i++;
						}
						CourtCase courtCase = CourtCase.builder().tenantId(TENANTID)
								.estateOfficerCourt(excelValues.get(0)).commissionersCourt(excelValues.get(1))
								.chiefAdministartorsCourt(excelValues.get(2)).advisorToAdminCourt(excelValues.get(3))
								.honorableDistrictCourt(excelValues.get(4)).honorableHighCourt(excelValues.get(5))
								.honorableSupremeCourt(excelValues.get(6))
								.propertyDetails(propertyDb.getPropertyDetails()).build();
						courtCase.setCreatedBy(SYSTEM);
						Set<CourtCase> courtCases = new HashSet<>();
						courtCases.add(courtCase);
						propertyDb.getPropertyDetails().setCourtCases(courtCases);
						propertyRepository.save(propertyDb);
						propertyList.add(propertyDb);
					} else {
							skippedFileNos.add(firstCell);
							log.error("We are skipping uploading property for file number: " + firstCell
									+ " as it does not exists.");
						
					}
				}
			}

		}

	}

	private class SheetContentsProcessorDoc implements StreamingRowProcessor {

		Set<String> skippedFileNos = new HashSet<>();
		List<Property> propertyList = new ArrayList<>();

		@Override
		public void processRow(Row currentRow) {
			File folder = new File(fileLocation);
			String[] listOfFiles = folder.list();
			List<String> filesList = Arrays.asList(listOfFiles);

			if (!filesList.isEmpty()) {
			if (currentRow.getRowNum() >= 2) {
				if (currentRow.getCell(0) != null) {
					String firstCell = String
							.valueOf(getValueFromCell(currentRow, 0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))
							.trim();
					String documentName = String
							.valueOf(getValueFromCell(currentRow, 3, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).trim();
					if (isNumeric(firstCell)) {
						firstCell = String.valueOf(Double.valueOf(firstCell).intValue());
					}
					if (filesList.contains(documentName)) {
						
						Property propertyDb = propertyRepository
									.getPropertyByFileNumber(firstCell);
					
					if (propertyDb != null) {
						byte[] bytes = null;
						List<HashMap<String, String>> response = null;
						try {
							bytes = Files.readAllBytes(Paths.get(folder + "/" + documentName));
							ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
							outputStream.write(bytes);
							String [] tenantId = propertyDb.getTenantId().split("\\.");
							response = fileStoreUtils.uploadStreamToFileStore(outputStream, tenantId[0],
									documentName);
							outputStream.close();
						} catch (IOException e) {
							log.error("error while converting file into byte output stream");
						}
						
						String documentType = String
								.valueOf(getValueFromCell(currentRow, 1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).trim();
						String documentFor = String
								.valueOf(getValueFromCell(currentRow, 4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).trim();
						String num = "";
						for (int i = 0; i < documentFor.length(); i++) {
							if (Character.isDigit(documentFor.charAt(i))) {
								num = num + documentFor.charAt(i);
							}
						}
						String docType = "";
						if (documentType.toUpperCase().contains("Certified copy of lease".toUpperCase())) {
							docType = "CERTIFIED_COPY_LEASE";
						} else if (documentType.toUpperCase().contains("Sub-Registrar, U.T Chandigarh".toUpperCase())) {
							docType = "NOTARIZED_COPY_DEED";
						} else if (documentType.toUpperCase().contains("Self-Attested photo identity proof of intending Transferee(s)/applicant(s)".toUpperCase())) {
							docType = "SELF_ATTESTED_PHOTO_IDENTITY_PROOF";
						} else if (documentType.toUpperCase().contains("Indemnity bond of transferee".toUpperCase())) {
							docType = "INDEMNITY_BOND_TRANSFEREE";
						} else if (documentType.toUpperCase().contains("Self-Attested photo Identity proofs of witnesses of Indemnity Bond".toUpperCase())) {
							docType = "SELF_ATTESTED_PHOTO_IDENTITY_PROOF_WITNESSES_INDEMITY_BOND";
						} else if (documentType.toUpperCase().contains("Clearance of previous mortgage".toUpperCase())) {
							docType = "CLEARANCE_PREVIOUS_MORTGAGE";
						} else if (documentType.toUpperCase().contains("Sewerage connection".toUpperCase())) {
							docType = "SEWERAGE_CONNECTION";
						} else if (documentType.toUpperCase().contains("Furnish proof of construction".toUpperCase())) {
							docType = "PROOF_OF_CONSTRUCTION";
						} else if (documentType.toUpperCase().contains("Indemnity Bond duly attested by Notary Public".toUpperCase())) {
							docType = "INDEMNITY_BOND";
						} else if (documentType.toUpperCase().contains("Notarized copy of GPA/SPA in case Sale/Gift/Transfer Deed as executed through GPA/SPA".toUpperCase())) {
							docType = "NOTARIZED_COPY_GPA_SPA";
						} else if (documentType.toUpperCase().contains("Affidavit regarding validity of GPA/SPA".toUpperCase())) {
							docType = "AFFIDAVIT_VALIDITY_GPA_SPA";
						} else if (documentType.toUpperCase().contains("Affidavit to the effect".toUpperCase())) {
							docType = "AFFIDAVIT_EFFECT";
						} else if (documentType.toUpperCase().contains("Attested copy of partnership deed".toUpperCase())) {
							docType = "ATTESTED_COPY_PARTNERSHIP_DEED";
						} else if (documentType.toUpperCase().contains("Copy of Memorandum".toUpperCase())) {
							docType = "COPY_OF_MEMORANDUM";
						} else if (documentType.toUpperCase().contains("No due Certificate of property tax".toUpperCase())) {
							docType = "NO_DUE_CERTIFICATE";
						} else if(documentType.equalsIgnoreCase("document a")) {
							docType = "DOCUMENT_A";
						} else if(documentType.equalsIgnoreCase("document b")) {
							docType = "DOCUMENT_B";
						} else if(documentType.equalsIgnoreCase("document c")) {
							docType = "DOCUMENT_C";
						} else if(documentType.equalsIgnoreCase("document d")) {
							docType = "DOCUMENT_D";
						}
						Document document = Document.builder()
								.tenantId(propertyDb.getTenantId()).active(true).documentType(docType)
								.fileStoreId(response.get(0).get("fileStoreId")).build();
						List<Owner> ownerList = null;
						if (documentFor.equalsIgnoreCase("Owner"+num)) {
							 ownerList = propertyDb
									.getPropertyDetails().getOwners().stream().filter(owner -> owner.getOwnerDetails()
											.getIsCurrentOwner().toString().equalsIgnoreCase("true"))
									.collect(Collectors.toList());
							Comparator<Owner> compare = (o1, o2) -> o1.getOwnerDetails().getCreatedTime()
									.compareTo(o2.getOwnerDetails().getCreatedTime());
							Collections.sort(ownerList, compare);
							//System.out.println(ownerList.size());
							//System.out
								//	.println(ownerList.get(Integer.parseInt(num) - 1).getOwnerDetails().getOwnerName());
							if(!ownerList.isEmpty()) {
							document.setReferenceId(ownerList.get(Integer.parseInt(num) - 1).getOwnerDetails().getId());
							document.setCreatedBy(SYSTEM);
							document.setProperty(propertyDb);
							Set<Document> documents = new HashSet<>();
							documents.add(document);
							propertyDb.setDocuments(documents);
							propertyRepository.save(propertyDb);
							propertyList.add(propertyDb);

							} else {
									skippedFileNos.add(firstCell);
									log.error("We are skipping uploading document for the property having file number: " + firstCell
											+ " as it does not have owner.");
								
							}
						} else if (documentFor.equalsIgnoreCase("Previous Owner"+num)) {
							 ownerList = propertyDb
									.getPropertyDetails().getOwners().stream().filter(owner -> owner.getOwnerDetails()
											.getIsCurrentOwner().toString().equalsIgnoreCase("false"))
									.collect(Collectors.toList());
							Comparator<Owner> compare = (o1, o2) -> o1.getOwnerDetails().getCreatedTime()
									.compareTo(o2.getOwnerDetails().getCreatedTime());
							Collections.sort(ownerList, compare);
							//System.out.println(ownerList.size());
							//System.out.println(ownerList.get(Integer.parseInt(num) - 1).getOwnerDetails().getOwnerName());
							if(!ownerList.isEmpty()) {
							document.setReferenceId(ownerList.get(Integer.parseInt(num) - 1).getOwnerDetails().getId());
							document.setCreatedBy(SYSTEM);
							document.setProperty(propertyDb);
							Set<Document> documents = new HashSet<>();
							documents.add(document);
							propertyDb.setDocuments(documents);
							propertyRepository.save(propertyDb);
							propertyList.add(propertyDb);

							} else {
									skippedFileNos.add(firstCell);
									log.error("We are skipping uploading document for the property having file number: " + firstCell
											+ " as it does not have previous owner.");
								
							}
							}
					} else {
							skippedFileNos.add(firstCell);
							log.error("We are skipping uploading property for file number: " + firstCell
									+ " as it does not exists.");
						
					}
					} else {
						skippedFileNos.add(firstCell);
						log.error("No Document with name "+ documentName + " is present in the document folder");
					}
				}
				}
			} else {
				throw new CustomException("NO_FILES_PRESENT", "No files present in document folder");
			}
		}

	}

	private PropertyResponse saveProperties(List<Property> properties, Set<String> skippedFileNos) {
		properties.forEach(property -> {
			propertyRepository.save(property);
		});
		PropertyResponse propertyResponse = PropertyResponse.builder().generatedCount(properties.size())
				.skippedFileNos(skippedFileNos).build();
		return propertyResponse;
	}

	private Boolean isNumeric(String value) {
		if (value != null && !value.matches("[1-9][0-9]*(\\.[0])?")) {
			return false;
		}
		return true;
	}

}
