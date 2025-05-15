package io.bokun.inventory.plugin.tourcms.model;

import io.bokun.inventory.plugin.api.rest.PricingCategory;
import io.bokun.inventory.plugin.api.rest.Rate;

import java.util.List;

public class ProductRateMapping {
    private List<Rate> rates;
    private List<String> startTimes;
    private List<PricingCategory> priceCategories;

    public ProductRateMapping(List<Rate> rates, List<String> startTimes, List<PricingCategory> priceCategories) {
        this.rates = rates;
        this.startTimes = startTimes;
        this.priceCategories = priceCategories;
    }

    public List<Rate> getRates() {
        return rates;
    }

    public void setRates(List<Rate> rates) {
        this.rates = rates;
    }

    public List<String> getStartTimes() {
        return startTimes;
    }

    public void setStartTimes(List<String> startTimes) {
        this.startTimes = startTimes;
    }

    public List<PricingCategory> getPriceCategories() {
        return priceCategories;
    }

    public void setPriceCategories(List<PricingCategory> priceCategories) {
        this.priceCategories = priceCategories;
    }
}
