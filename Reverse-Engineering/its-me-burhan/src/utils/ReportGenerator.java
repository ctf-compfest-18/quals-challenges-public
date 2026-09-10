package utils;

import exception.BurhanQuestException;
import exception.DataFileException;

import services.GameManager;

import utils.enums.AdminIOCsvMode;
import utils.interfaces.DataExportable;
import utils.interfaces.DataImportable;

public class ReportGenerator implements DataExportable, DataImportable {

    private final ReportExporter exporter;
    private final ReportImporter importer;

    public ReportGenerator(GameManager gameManager) {
        ConsoleTablePrinter printer = new ConsoleTablePrinter();
        this.exporter = new ReportExporter(gameManager, printer);
        this.importer = new ReportImporter(gameManager, printer);
    }

    public void previewExport(AdminIOCsvMode mode) throws DataFileException {
        exporter.previewExport(mode);
    }

    public void previewQuestReport() throws DataFileException {
        exporter.previewExport(AdminIOCsvMode.QUEST);
    }

    public void previewWandererReport() throws DataFileException {
        exporter.previewExport(AdminIOCsvMode.WANDERER);
    }

    @Override
    public void exportToCsv(String path, AdminIOCsvMode mode) throws DataFileException {
        exporter.exportToCsv(path, mode);
    }

    @Override
    public void exportToTxt(String path, AdminIOCsvMode mode) throws DataFileException {
        exporter.exportToTxt(path, mode);
    }

    @Override
    public void importFromCsv(String path, AdminIOCsvMode mode) throws BurhanQuestException {
        importer.importFromCsv(path, mode);
    }
}
