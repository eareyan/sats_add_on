import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import org.spectrumauctions.sats.core.model.Bundle;
import org.spectrumauctions.sats.core.model.gsvm.GSVMBidder;
import org.spectrumauctions.sats.core.model.gsvm.GSVMLicense;
import org.spectrumauctions.sats.core.model.gsvm.GSVMWorld;
import org.spectrumauctions.sats.core.model.gsvm.GlobalSynergyValueModel;
import org.spectrumauctions.sats.core.model.lsvm.LSVMBidder;
import org.spectrumauctions.sats.core.model.lsvm.LSVMLicense;
import org.spectrumauctions.sats.core.model.lsvm.LSVMWorld;
import org.spectrumauctions.sats.core.model.lsvm.LocalSynergyValueModel;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.*;

class ValueModelDraw {
    /**
     * Represents a draw of either the LSVM or GSVM model.
     * A draw consists of a list of bidders, each with all possible values for
     * all possible bundles (for which they have non-zero value).
     */
    public List<BidderValues> bidder_values;

    public ValueModelDraw(List<BidderValues> bidder_values) {
        this.bidder_values = bidder_values;
    }
}

class Tuple<L, R> {
    /**
     * A simple tuple class. We annotate with how we want to print values for the json file.
     */
    @SerializedName("bundle")
    public final L l;
    @SerializedName("value")
    public final R r;

    public Tuple(L l, R r) {
        this.l = l;
        this.r = r;
    }
}

class BidderValues {
    /**
     * Represents the values for bundles a bidder has.
     */
    public int id;
    public List<Integer> preferred_licences;
    public List<Tuple<List<Integer>, BigDecimal>> values;

    public BidderValues(int id, List<Integer> preferred_licences, List<Tuple<List<Integer>, BigDecimal>> values) {
        this.id = id;
        this.preferred_licences = preferred_licences;
        Collections.sort(this.preferred_licences);
        this.values = values;
    }
}

class CompareBundleValueTuple implements Comparator<Tuple<List<Integer>, BigDecimal>> {
    /**
     * Used to order the way in which bundle and values are reported.
     *
     * @param leftBundleValueTuple  the first bundle, value.
     * @param rightBundleValueTuple the second bundle, value
     * @return 1, -1, or 0
     */
    @Override
    public int compare(Tuple<List<Integer>, BigDecimal> leftBundleValueTuple, Tuple<List<Integer>, BigDecimal> rightBundleValueTuple) {
        if (leftBundleValueTuple.l.size() > rightBundleValueTuple.l.size()) {
            return 1;
        } else if (leftBundleValueTuple.l.size() < rightBundleValueTuple.l.size()) {
            return -1;
        } else {
            return leftBundleValueTuple.r.compareTo(rightBundleValueTuple.r);
        }
    }
}

class ValueBidder {
    /**
     * A triage class to handle both LSVM and GSVM bidders.
     */

    public final Tuple<LSVMBidder, GSVMBidder> bidderTuple;
    public final Tuple<List<LSVMLicense>, List<GSVMLicense>> licensesTuple;
    public final String type;

    public ValueBidder(Tuple<LSVMBidder, GSVMBidder> bidderTuple,
                       Tuple<List<LSVMLicense>, List<GSVMLicense>> licensesTuple,
                       String type) throws Exception {
        this.bidderTuple = bidderTuple;
        this.licensesTuple = licensesTuple;
        this.type = type;
        if (!type.equals("LSVM") && !type.equals("GSVM")) {
            throw new Exception("Either LSVM or GSVM");
        }
    }

    public Map<Long, BigDecimal> getBaseValues() {
        if (this.type.equals("LSVM")) {
            return this.bidderTuple.l.getBaseValues();
        } else {
            return this.bidderTuple.r.getBaseValues();
        }
    }

    public BigDecimal calculateValue(List<Integer> bundle) {
        if (this.type.equals("LSVM")) {
            return this.bidderTuple.l.calculateValue(this.get_value_bundle(bundle).l);
        } else {
            return this.bidderTuple.r.calculateValue(this.get_value_bundle(bundle).r);
        }
    }

    public long getId() {
        if (this.type.equals("LSVM")) {
            return this.bidderTuple.l.getId();
        } else {
            return this.bidderTuple.r.getId();
        }
    }

    public Tuple<Bundle<LSVMLicense>, Bundle<GSVMLicense>> get_value_bundle(List<Integer> bundle) {
        if (this.type.equals("LSVM")) {
            Bundle<LSVMLicense> lsvm_bundle = new Bundle<>();
            for (Integer lic : bundle) {
                lsvm_bundle.add(this.licensesTuple.l.get(lic));
            }
            return new Tuple<>(lsvm_bundle, null);
        } else {
            Bundle<GSVMLicense> gsvm_bundle = new Bundle<>();
            for (Integer lic : bundle) {
                gsvm_bundle.add(this.licensesTuple.r.get(lic));
            }
            return new Tuple<>(null, gsvm_bundle);
        }
    }
}


public class LSMVJsonGen {

    /* A helper to enumerates all possible subsets of a given list */
    public static Set<List<Integer>> enumerate_helper(List<Integer> left, List<Integer> right) {
        if (right.size() == 0) {
            return new HashSet<>() {{
                add(left);
            }};
        } else {
            // Do not select the first element from the right
            List<Integer> leftCopy_1 = new ArrayList<>(left);
            List<Integer> rightCopy_1 = new ArrayList<>(right.subList(1, right.size()));
            Set<List<Integer>> b = enumerate_helper(leftCopy_1, rightCopy_1);

            // Select the first element from the right, move it to left
            List<Integer> leftCopy_2 = new ArrayList<>(left);
            List<Integer> rightCopy_2 = new ArrayList<>(right.subList(1, right.size()));
            leftCopy_2.add(right.get(0));
            Set<List<Integer>> a = enumerate_helper(leftCopy_2, rightCopy_2);

            // Return result
            Set<List<Integer>> returnList = new HashSet<>();
            returnList.addAll(a);
            returnList.addAll(b);
            return returnList;
        }
    }

    /* Enumerates all possible subsets of a given list */
    public static Set<List<Integer>> enumerate(List<Integer> listOfNumbers) {
        return LSMVJsonGen.enumerate_helper(new ArrayList<>(), listOfNumbers);
    }

    public static ValueModelDraw create_lsvm_draw(List<ValueBidder> bidders, Map<Integer, Set<Integer>> ignore_licenses) {
        // Data structures to collect results
        List<BidderValues> lsvmBidderValues = new ArrayList<>();

        // For each bidder, find the bundles the bidder is interested in.
        for (ValueBidder bidder : bidders) {

            // Licenses are given as Long. Collect them in a list of integers so that we can enumerate over them.
            List<Integer> bidders_licenses = new ArrayList<>();
            for (Long k : bidder.getBaseValues().keySet()) {
                // Check which licenses to ignore.
                if (ignore_licenses.containsKey((int) bidder.getId()) &&
                        ignore_licenses.get((int) bidder.getId()) != null &&
                        ignore_licenses.get((int) bidder.getId()).contains(k.intValue())) {
                    continue;
                }
                bidders_licenses.add(k.intValue());
            }

            // Enumerate all possible bundles.
            List<Tuple<List<Integer>, BigDecimal>> bidder_bundles_values_list = new ArrayList<>();
            Set<List<Integer>> all_bundles = enumerate(bidders_licenses);
            for (List<Integer> bundle : all_bundles) {
                bidder_bundles_values_list.add(new Tuple<>(bundle, bidder.calculateValue(bundle).setScale(8, BigDecimal.ROUND_HALF_UP)));
            }
            bidder_bundles_values_list.sort(new CompareBundleValueTuple());
            BidderValues bidder_value = new BidderValues((int) bidder.getId(), bidders_licenses, bidder_bundles_values_list);
            lsvmBidderValues.add(bidder_value);
        }
        return new ValueModelDraw(lsvmBidderValues);
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("log4j.configurationFile", "log4j2.xml");

        // Check we have the right number of parameters.
        if (args.length != 1 && args.length != 2) {
            throw new Exception("Either 1 or 2 arguments. ");
        }

        // Get the model type.
        String model_type = args[0];

        if (!model_type.equals("LSVM") && !model_type.equals("LSVM2") && !model_type.equals("GSVM")) {
            throw new Exception("Model type must be either LSVM, LSVM2 or GSVM");
        }

        // Get the output file location.
        String jsonOutputFileLocation;
        if (args.length == 1) {
            jsonOutputFileLocation = "default.json";
        } else {
            jsonOutputFileLocation = args[1];
        }

        List<ValueBidder> valueBidders = new ArrayList<>();
        if (model_type.equals("LSVM") || model_type.equals("LSVM2")) {
            // Create LSVM model.
            LocalSynergyValueModel lsvm = new LocalSynergyValueModel();
            LSVMWorld lsvmWorld = lsvm.createWorld();
            List<LSVMLicense> lsvmLicenses = lsvmWorld.getLicenses().asList();
            List<LSVMBidder> lsvmBidders = lsvm.createPopulation(lsvmWorld);
            for (LSVMBidder lsvmBidder : lsvmBidders) {
                valueBidders.add(new ValueBidder(new Tuple<>(lsvmBidder, null), new Tuple<>(lsvmLicenses, null), "LSVM"));
            }
        } else {
            // Create GSVM model.
            GlobalSynergyValueModel gsvm = new GlobalSynergyValueModel();
            GSVMWorld gsvmWorld = gsvm.createWorld();
            List<GSVMLicense> gsvmLicenses = gsvmWorld.getLicenses().asList();
            List<GSVMBidder> gsvmBidders = gsvm.createPopulation(gsvmWorld);
            for (GSVMBidder gsvmBidder : gsvmBidders) {
                valueBidders.add(new ValueBidder(new Tuple<>(null, gsvmBidder), new Tuple<>(null, gsvmLicenses), "GSVM"));
            }
        }

        // Create ignore map. Hard-coded for LSVM2, for the moment.
        Map<Integer, Set<Integer>> ignore_licenses = new HashMap<>();
        if (model_type.equals("LSVM2")) {
            ignore_licenses.put(0, new HashSet<>() {{
                add(0);
                add(5);
                add(6);
                add(11);
                add(12);
                add(17);
            }});
        }

        // Create and save JSON file.
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json_lsvm_draw = gson.toJson(create_lsvm_draw(valueBidders, ignore_licenses));
        PrintWriter out = new PrintWriter(jsonOutputFileLocation);
        out.println(json_lsvm_draw);
        out.close();

    }
}
