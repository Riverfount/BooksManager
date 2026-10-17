package com.riverfount.booksmanager.catalog.application.port.in;

import java.util.List;

/**
 * The catalog numbers of the dashboard (RF25): the active copies in all and by category, in order of
 * category name. An active category with nothing in it is listed with zeros; an inactive one only while it
 * still holds books or copies (RN14).
 */
public record CatalogSummary(long totalCopies, List<CategoryCopies> categories) {
}
