import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.spectrumauctions.sats.core.model.Bundle;
import org.spectrumauctions.sats.core.model.lsvm.LSVMBidder;
import org.spectrumauctions.sats.core.model.lsvm.LSVMLicense;
import org.spectrumauctions.sats.core.model.lsvm.LSVMWorld;
import org.spectrumauctions.sats.core.model.lsvm.LocalSynergyValueModel;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.*;

class LSVMDraw {
    /**
     * Represents a draw of the LSVM model. A draw consists of a list of bidder, each with all possible values for
     * all possible bundles (for which they have non-zero value).
     */
    public List<LSVMBidderValues> bidder_values;

    public LSVMDraw(List<LSVMBidderValues> bidder_values) {
        this.bidder_values = bidder_values;
    }
}

class BundleValueTuple {
    /**
     * A (Bundle, Value) tuple.
     */
    public List<Integer> bundle;
    public BigDecimal value;

    public BundleValueTuple(List<Integer> bundle, BigDecimal value) {
        this.bundle = bundle;
        Collections.sort(this.bundle);
        this.value = value;
    }
}

class LSVMBidderValues {
    /**
     * Represents the values for bundles a bidder has.
     */
    public int id;
    public List<Integer> preferred_licences;
    public List<BundleValueTuple> values;

    public LSVMBidderValues(int id, List<Integer> preferred_licences, List<BundleValueTuple> values) {
        this.id = id;
        this.preferred_licences = preferred_licences;
        Collections.sort(this.preferred_licences);
        this.values = values;

    }
}

class CompareBundleValueTuple implements Comparator<BundleValueTuple> {
    /**
     * Used to order the way in which bundle and values are reported.
     *
     * @param leftBundleValueTuple  the first bundle, value.
     * @param rightBundleValueTuple the second bundle, value
     * @return 1, -1, or 0
     */
    @Override
    public int compare(BundleValueTuple leftBundleValueTuple, BundleValueTuple rightBundleValueTuple) {
        if (leftBundleValueTuple.bundle.size() > rightBundleValueTuple.bundle.size()) {
            return 1;
        } else if (leftBundleValueTuple.bundle.size() < rightBundleValueTuple.bundle.size()) {
            return -1;
        } else {
            return leftBundleValueTuple.value.compareTo(rightBundleValueTuple.value);
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
            //Set<List<Integer>> returnList = new TreeSet<>(new CompareSubSets());
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

    public static void main(String[] args) throws FileNotFoundException {
        String jsonOutputFileLocation;
        if (args.length == 0) {
            jsonOutputFileLocation = "default.json";
        } else {
            jsonOutputFileLocation = args[0];
        }

        // Data structures to collect results
        List<LSVMBidderValues> lsvmBidderValues = new ArrayList<>();

        // Create LSVM model.
        LocalSynergyValueModel lsvm = new LocalSynergyValueModel();
        LSVMWorld world = lsvm.createWorld();
        List<LSVMLicense> licenses = world.getLicenses().asList();
        List<LSVMBidder> bidders = lsvm.createPopulation(world);

        // For each bidder, find the bundles the bidder is interested in.
        for (LSVMBidder bidder : bidders) {

            // Ignoring the National bidder, for now.
            if (bidder.getId() == 0) continue;

            // Licenses are given as Long. Collect them in a list of integers so that we can enumerate over them.
            List<Integer> bidders_licenses = new ArrayList<>();
            for (Long k : bidder.getBaseValues().keySet()) {
                bidders_licenses.add(k.intValue());
            }

            // Enumerate all possible bundles.
            List<BundleValueTuple> bidder_bundles_values_list = new ArrayList<>();
            Set<List<Integer>> all_bundles = enumerate(bidders_licenses);
            for (List<Integer> bundle : all_bundles) {
                Bundle<LSVMLicense> lsvm_bundle = new Bundle<>();
                for (Integer lic : bundle) {
                    lsvm_bundle.add(licenses.get(lic));
                }
                //System.out.println(lsvm_bundle);
                bidder_bundles_values_list.add(new BundleValueTuple(bundle, bidder.calculateValue(lsvm_bundle).setScale(8, BigDecimal.ROUND_HALF_UP)));
            }
            bidder_bundles_values_list.sort(new CompareBundleValueTuple());
            LSVMBidderValues bidder_value = new LSVMBidderValues((int) bidder.getId(), bidders_licenses, bidder_bundles_values_list);
            lsvmBidderValues.add(bidder_value);
        }
        LSVMDraw lsvm_draw = new LSVMDraw(lsvmBidderValues);

        // Create and save JSON file.
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json_lsvm_draw = gson.toJson(lsvm_draw);
        PrintWriter out = new PrintWriter(jsonOutputFileLocation);
        out.println(json_lsvm_draw);
        out.close();

    }
}
