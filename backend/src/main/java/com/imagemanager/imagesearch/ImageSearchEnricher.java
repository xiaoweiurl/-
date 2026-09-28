package com.imagemanager.imagesearch;

import java.util.List;

public interface ImageSearchEnricher {

    List<ImageSearchModels.ImageSearchHitView> enrich(List<ImageSearchModels.RawHit> hits, String company);
}
