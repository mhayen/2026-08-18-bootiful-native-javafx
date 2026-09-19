package nl.markhayen.desktop.model;

import java.util.List;

public record SearchResponse(List<DriveFile> files, String nextPageToken, String kind, Boolean incompleteSearch) {
}
