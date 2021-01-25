package no.fint.personalmappe.service;

import no.fint.personalmappe.model.MongoDBPersonalmappe;
import no.fint.personalmappe.repository.MongoDBRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileService {

    public static String TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static String[] HEADERs = {"Username", "Leader", "Workplace", "OrgId", "Association", "Status", "Message", "Version", "CreatedDate", "LastModifiedDate"};
    static String SHEET = "Personalmapper";
    @Autowired
    private MongoDBRepository mongoDBRepository;


    public InputStream getFile(String orgId, String status, String searchValue) throws IOException {

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {
            Sheet sheet = workbook.createSheet(SHEET);
            List<MongoDBPersonalmappe> mongoDBPersonalmappes = createInitialWorkBook(workbook, sheet, orgId);

            int rowIdx = 1;
            for (MongoDBPersonalmappe mappe : mongoDBPersonalmappes) {
                if (status.equals(mappe.getStatus().name()) || status.equals("all")) {
                    if (searchValue.equals("nosearchvalue") || mappe.getUsername().toLowerCase().contains(searchValue.toLowerCase())) {
                        Row row = sheet.createRow(rowIdx++);
                        fillRow(row, mappe);
                    }
                }
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("fail to import data to Excel file: " + e.getMessage());
        }
    }

    private void fillRow(Row row, MongoDBPersonalmappe mappe) {
        row.createCell(0).setCellValue(mappe.getUsername());
        row.createCell(1).setCellValue(mappe.getLeader());
        row.createCell(2).setCellValue(mappe.getWorkplace());
        row.createCell(3).setCellValue(mappe.getOrgId());
        row.createCell(4).setCellValue(mappe.getAssociation() != null ? mappe.getAssociation().toString() : null);
        row.createCell(5).setCellValue(mappe.getStatus().name());
        row.createCell(6).setCellValue(mappe.getMessage());
        row.createCell(7).setCellValue(mappe.getVersion());
        row.createCell(8).setCellValue(mappe.getCreatedDate().toString());
        row.createCell(9).setCellValue(mappe.getLastModifiedDate().toString());
    }

    private List<MongoDBPersonalmappe> createInitialWorkBook(Workbook workbook, Sheet sheet, String orgId) {

        // Header
        Row headerRow = sheet.createRow(0);

        for (int col = 0; col < HEADERs.length; col++) {
            Cell cell = headerRow.createCell(col);
            cell.setCellValue(HEADERs[col]);
        }
        return mongoDBRepository
                .findAll(Sort.by(Sort.Direction.DESC, "lastModifiedDate"))
                .stream()
                .filter(pm -> pm.getOrgId().equals(orgId))
                .collect(Collectors.toList());
    }
}
