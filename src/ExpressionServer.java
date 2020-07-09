import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpressionServer implements Callable<String> {

    private final ArrayList<String> orderVariable;
    private final String input;


    public ExpressionServer(String input) {

        orderVariable = new ArrayList<>();
        this.input = input;
    }

    public String call() {

        String computationKind;
        String valuesKind;
        String variableValuesFunction;
        String[] expressions;

        String[] inputToken = input.split(";");
        String[] type = inputToken[0].split("_");
        if (type.length != 2) return ("Unrecognized request type '" + inputToken[0] + "'");
        else {
            computationKind = type[0];
            valuesKind = type[1];
        }
        variableValuesFunction = inputToken[1];
        expressions = new String[inputToken.length - 2];
        System.arraycopy(inputToken, 2, expressions, 0, expressions.length);
        if (expressions.length == 0) return ("Invalid Request(no expressions)");
        //parsing domain values of variable
        SortedMap<String, Double[]> variableMap = new TreeMap<>();
        String[] vvfs = variableValuesFunction.split(",");
        Pattern p = Pattern.compile("[a-z][a-z0-9]*");
        for (String vvf : vvfs) {
            String[] variableDomainsString = vvf.split(":");
            if (variableDomainsString.length != 4) return ("Invalid variableValuesFunction domain: " + vvf);
            Matcher m = p.matcher(variableDomainsString[0]);
            if (!m.matches()) return ("Invalid VarName: " + variableDomainsString[0]);
            if (variableMap.containsKey(variableDomainsString[0]))
                return ("Invalid variableValuesFunction domain: VarName '" + variableDomainsString[0] + "' already defined");
            Double[] variableDomains = new Double[3];
            for (int i = 1; i < variableDomainsString.length; i++) {
                try {
                    variableDomains[i - 1] = Double.parseDouble(variableDomainsString[i]);
                } catch (NumberFormatException e) {
                    return ("Invalid " + variableDomainsString[0] + " domain - impossible to convert '" + variableDomainsString[i] + "' to double");
                }
            }
            if (variableDomains[1] <= 0)
                return ("Invalid '" + variableDomainsString[0] + "' domain - step <= 0");
            if (variableDomains[0] > variableDomains[2])
                return ("Invalid '" + variableDomainsString[0] + "' domain - Upperbound < Lowerbound)");
            variableMap.put(variableDomainsString[0], variableDomains);
        }

        List<List<Double>> tuples = new ArrayList<>();
        Collection<Double[]> domainTuples = variableMap.values();
        domainTuples.forEach(value -> tuples.add(getDomainValues(value)));

        Set<String> keys = variableMap.keySet();
        orderVariable.addAll(keys);

        List<List<Double>> processedTuples = new ArrayList<>();

        if (valuesKind.equals("LIST")) {
            for (int i = 0; i < tuples.get(0).size(); i++) {
                List<Double> buffer = new ArrayList<>();
                for (int j = 0; j < tuples.size(); j++) {
                    if (j < tuples.size() - 1 && tuples.get(j).size() != tuples.get(j + 1).size())
                        return ("Impossible to List (domain with different sizes)");
                    buffer.add(tuples.get(j).get(i));
                }
                processedTuples.add(buffer);
            }
        } else if (valuesKind.equals("GRID")) {
            processedTuples = Lists.cartesianProduct(tuples);
        } else return ("Invalid valuesKind:" + valuesKind);

        if (computationKind.equals("COUNT")) return String.valueOf(processedTuples.size());

        List<Double> processedExpressionValues = new ArrayList<>();
        try {
            for (String s : expressions) {
                checkExpression(s);
                Parser parser = new Parser(s);
                Node root = parser.parse();
                if (parser.getCursor() != s.length()) throw new IllegalArgumentException(s);
                List<Double> expressionValues = new ArrayList<>();
                for (List<Double> values : processedTuples) {
                    expressionValues.add(calculate(root, values));
                }
                switch (computationKind) {
                    case "MAX":
                        processedExpressionValues.add(Collections.max(expressionValues));
                        break;
                    case "MIN":
                        processedExpressionValues.add(Collections.min(expressionValues));
                        break;
                    case "AVG":
                        return String.valueOf(calculateAverage(expressionValues));
                }

            }
        } catch (IllegalArgumentException e) {
            return ("Invalid Expression(" + e.getMessage() + ")");
        }

        switch (computationKind) {
            case "MAX":
                return String.valueOf(Collections.max(processedExpressionValues));
            case "MIN":
                return String.valueOf(Collections.min(processedExpressionValues));
        }
        return ("Invalid computationRequest");
    }

    private static List<Double> getDomainValues(Double[] domain) {

        List<Double> result = new ArrayList<>();
        for (int i = 0; i < ((domain[2] - domain[0]) / domain[1]) + 1; i++) {
            result.add(domain[0] + i * domain[1]);
        }
        return result;
    }

    private void checkExpression(String exp) throws IllegalArgumentException {
        if (exp.length() == 0) throw new IllegalArgumentException("Empty String");
        int cont = 0;
        for (int i = 0; i < exp.length(); i++) {
            char buffer = exp.charAt(i);
            if (buffer == '(') cont++;
            if (buffer == '*' || buffer == '/' || buffer == '+' || buffer == '-' || buffer == '^') cont--;
        }
        if (cont != 0) throw new IllegalArgumentException(exp);
    }

    private double calculate(Node node, List<Double> tupla) throws IllegalArgumentException {
        if (node.getClass() == Constant.class) return ((Constant) node).getValue();
        else if (node.getClass() == Variable.class) {
            int index;
            if ((index = getIndex(((Variable) node).getName())) < 0)
                throw new IllegalArgumentException("Unvalued variable " + ((Variable) node).getName());
            else return tupla.get(index);
        } else {
            Function<double[], Double> operator = ((Operator) node).getType().getFunction();
            double[] value = new double[2];
            value[0] = calculate(node.getChildren().get(0), tupla);
            value[1] = calculate(node.getChildren().get(1), tupla);
            return operator.apply(value);
        }
    }

    private int getIndex(String name) {
        for (int i = 0; i < orderVariable.size(); i++) {
            if (orderVariable.get(i).equals(name)) return i;
        }
        return -1;
    }

    private double calculateAverage(List<Double> marks) {
        Double sum = 0.0;
        for (Double mark : marks) {
            sum += mark;
        }
        return sum / marks.size();
    }
}

