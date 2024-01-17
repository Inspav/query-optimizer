package sjdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Optimiser {

    private Catalogue catalogue;

    public Optimiser(Catalogue cat) {
        catalogue = cat;
    }

    public Operator optimise(Operator op) {
        Operator output;
        output = moveSelectDown(op);
        output = swapJoinsAround(output);
        output = combineSelectAndProduct(output);
        output = moveProjectDown(output);
        return output;
    }
    // Functions for moving the select operator down
    private Operator moveSelectDown(Operator op) {
        Operator temp;
        // If op is a selection begin moving it down

        if (op instanceof Select) {

            // If we find a scan at the end of the tree
            if(((Select) op).getInput() instanceof Scan){
                Predicate predicate = getUniquePredicate(((Select) op).getPredicate());
                temp = new Select(moveSelectDown(((Select) op).getInput()), predicate);
            }
            // if we find another Select
            else if (((Select) op).getInput() instanceof Select) {
                temp = moveSelectDown(new Select(moveSelectDown(((Select) op).getInput()), getUniquePredicate(((Select) op).getPredicate())));
            }
            // if we find a project
            else if (((Select) op).getInput() instanceof Project) {
                temp = new Project(moveSelectDown(new Select(((Project) ((Select) op).getInput()).getInput(), getUniquePredicate(((Select) op).getPredicate()))), getUniqueAttributes(((Project) ((Select) op).getInput()).getAttributes()));
            }
            // if we find a product
            else if (((Select) op).getInput() instanceof Product) {

                Operator left = ((Product) ((Select) op).getInput()).getLeft();
                Operator right = ((Product) ((Select) op).getInput()).getRight();
                Predicate predicate = ((Select) op).getPredicate();


                // if the select is of type att=var
                if (((Select) op).getPredicate().equalsValue()) {
                    // if we find the attribute on the left
                    if (containsAttribute(left, ((Select) op).getPredicate().getLeftAttribute())) {
                        temp = new Product(moveSelectDown(new Select(left, getUniquePredicate(((Select) op).getPredicate()))), moveSelectDown(right));
                    } else if (containsAttribute(right, ((Select) op).getPredicate().getLeftAttribute())) {
                        temp = new Product(moveSelectDown(left), moveSelectDown(new Select(right, getUniquePredicate(((Select) op).getPredicate()))));
                    } else {
                        temp = new Select(moveSelectDown(((Select) op).getInput()), getUniquePredicate(((Select) op).getPredicate()));
                    }
                } else { // if it's of type att=att

                    // TODO WILL NOT BREAK BUT IS VERY BULKY
                    if (containsAttribute(left, ((Select) op).getPredicate().getLeftAttribute()) && containsAttribute(left, ((Select) op).getPredicate().getRightAttribute()) && !containsAttribute(right, ((Select) op).getPredicate().getLeftAttribute()) && !containsAttribute(right, ((Select) op).getPredicate().getRightAttribute())) {
                        temp = new Product(moveSelectDown(new Select(((Select) op).getInput(), getUniquePredicate(predicate))), moveSelectDown(right));
                    } else if (containsAttribute(right, ((Select) op).getPredicate().getLeftAttribute()) && containsAttribute(right, ((Select) op).getPredicate().getRightAttribute()) && !containsAttribute(left, ((Select) op).getPredicate().getLeftAttribute()) && !containsAttribute(left, ((Select) op).getPredicate().getRightAttribute())) {
                        temp = new Product(moveSelectDown(left), new Select(((Select) op).getInput(), getUniquePredicate(predicate)));
                    } else {
                        temp = new Select(moveSelectDown(((Select) op).getInput()), getUniquePredicate(((Select) op).getPredicate()));
                    }
                }
            }
        } else if (op instanceof Project) {
            temp = new Project(moveSelectDown(((Project) op).getInput()), getUniqueAttributes(((Project) op).getAttributes()));
        } else if (op instanceof Scan){
            NamedRelation namedRelation = (NamedRelation) ((Scan) op).getRelation();
            return new Scan(namedRelation);
        } else if (op instanceof Product) {
            temp = new Product(moveSelectDown(((Product) op).getLeft()), moveSelectDown(((Product) op).getRight()));
        }
        return op;
    }

    private Predicate getUniquePredicate(Predicate predicate) {
        Predicate tempPredicate = predicate;
        Attribute left = new Attribute(tempPredicate.getLeftAttribute().getName());
        Predicate p;

        // if right is a value make a new predicate with that value
        if (predicate.equalsValue()) {
            String value = tempPredicate.getRightValue();
            p = new Predicate(left, value);

        }else {
            Attribute right = new Attribute(tempPredicate.getRightAttribute());
            p = new Predicate(left, right);
        }

        return p;
    }

    private List<Attribute> getUniqueAttributes(List<Attribute> input) {
        List<Attribute> output = new ArrayList<>();

        for (Attribute att : input) {
            Attribute temp = new Attribute(att.getName());
            output.add(temp);
        }

        return output;
    }

    private boolean containsAttribute(Operator op, Attribute a) {
        if (op instanceof Scan) {
            return attributeEquals(a, ((Scan) op).getRelation().getAttributes());// TODO may cause issue
        } else if (op instanceof Product) {
            return containsAttribute(((Product) op).getLeft(), a) || containsAttribute(((Product) op).getRight(),a);
        } else if (op instanceof Project) {
            return containsAttribute(((Project) op).getInput(), a);
        } else if (op instanceof Select) {
            return containsAttribute(((Select) op).getInput(), a);
        }
        else return false;
    }

    // End of functions for moving the select operator down


    private Operator combineSelectAndProduct(Operator op) {
        Operator output;

        if (op instanceof Project) {
            Operator temp = ((Project) op).getInput();
            List<Attribute> list = getUniqueAttributes(((Project) op).getAttributes());

            output = new Project(combineSelectAndProduct(((Project) op).getInput()), list);
        } else if (op instanceof Product) {
            output = new Product(combineSelectAndProduct(((Product) op).getLeft()), combineSelectAndProduct(((Product) op).getRight()));
        } else if (op instanceof Select) {
            Predicate predicate = getUniquePredicate(((Select) op).getPredicate());
            if (((Select) op).getInput() instanceof Product) {
                Operator left = ((Product) ((Select) op).getInput()).getLeft();
                Operator right = ((Product) ((Select) op).getInput()).getRight();


                output = new Join(moveSelectDown(left), moveSelectDown(right), predicate);
            } else {
                output = new Select(combineSelectAndProduct(((Select) op).getInput()), predicate);
            }
        } else if (op instanceof Scan){
            output = new Scan((NamedRelation) ((Scan) op).getRelation());
        } else {
            output = null;
        }

        return output;
    }

    // Start of functions for for swapping joins

    private Operator swapJoinsAround (Operator op) {
        Operator output = null;

        ArrayList<Operator> smallSelects = new ArrayList<>();
        getSmallSelects(op, smallSelects);

        ArrayList<Select> joinList = new ArrayList<>();
        getJoinList(op, joinList);

        Inspector inspector = new Inspector();

        for (Operator oper : smallSelects) {
            //oper.accept(inspector);
        }
        for (Select sel : joinList) {
            //sel.accept(inspector);
        }

        ArrayList<Operator[]> allJoinPairing = getAllJoinPairings(smallSelects, joinList);

        for (Operator[] operators : allJoinPairing) {
            //operators[1].accept(inspector);
        }

        ArrayList<Operator[]> fromCheap = getPairsFromCheap(allJoinPairing);

        output = swapHelper(op, fromCheap);

        return output;
    }

    private Operator swapHelper(Operator op, ArrayList<Operator[]> operators) {
        Operator output = null;
        boolean firstPairPassed = false;

        for (Operator[] operator : operators ) {
            if (!firstPairPassed) {
                Operator left = operator[0];
                Operator right = operator[1];
                Operator join = operator[2];
                firstPairPassed = true;
                output = reconstructJoin(join, left, right);
            } else {
                Operator right = operator[0];
                Operator join = operator[2];
                Operator temp = reconstructJoin(join, output, right);
                output = temp;
            }

            if (op instanceof Project) {
                Operator last = new Project(output, getUniqueAttributes(((Project) op).getAttributes()));
                output = last;
            }

        } return output;
    }

    private Operator reconstructJoin(Operator join, Operator left, Operator right) {
        Operator output = null;

        if (join instanceof Select) {

            if (isInCorrectSpot(right, ((Select) join).getPredicate().getRightAttribute())) {
                output = new Select(reconstructJoin(((Select) join).getInput(), left, right), getUniquePredicate(((Select) join).getPredicate()));
            } else {
                Predicate predicate = new Predicate(((Select) join).getPredicate().getRightAttribute(), ((Select) join).getPredicate().getLeftAttribute());
                output = new Select(reconstructJoin(((Select) join).getInput(), left, right), predicate);
            }


        } else if (join instanceof Product) {
            output = new Product(left, right);
        }
        return output;
    }

    private boolean isInCorrectSpot(Operator op, Attribute att) {
        for (Attribute attribute : op.getOutput().getAttributes()) {
            if (attribute.getName().equals(att.getName())) return true;
        }
        return false;
    }


    // TODO may cause issue
    private ArrayList<Operator[]> getPairsFromCheap(ArrayList<Operator[]> allJoinPairs) {
        ArrayList<Operator[]> output = new ArrayList<>();
        ArrayList<Operator[]> allPairsRemove = new ArrayList<>();
        ArrayList<Operator> includedOps = new ArrayList<>();

        Operator[] smallestPair = getSmallestPair(allJoinPairs);

        Inspector inspector = new Inspector();
        //smallestPair[0].accept(inspector);
        //smallestPair[1].accept(inspector);

        for (Operator[] pair : allJoinPairs) {
            if (!pair.equals(smallestPair)) {
                //pair[0].accept(inspector);
                //pair[1].accept(inspector);
                allPairsRemove.add(pair);}


        }

        output.add(smallestPair);
        includedOps.add(smallestPair[0]);
        includedOps.add(smallestPair[1]);

        while (!allPairsRemove.isEmpty()) {
            Operator[] smallest = nextSmallestPair(allPairsRemove, includedOps);
            output.add(smallest);
            includedOps.add(smallest[0]);
            includedOps.add(smallest[1]);
            allPairsRemove.remove(smallest);
        } // TODO check if added in right order

        return output;
    }

    private Operator[] nextSmallestPair(ArrayList<Operator[]> allPairsRemove, ArrayList<Operator> includedOps) {
        Operator[] output = null;


        for (Operator[] operators : allPairsRemove) {
            if (output != null) {

                int currentCost = output[0].getOutput().getTupleCount() *  output[1].getOutput().getTupleCount();
                int potentialCost = operators[0].getOutput().getTupleCount() *  operators[1].getOutput().getTupleCount();

                if (includedOps.contains(operators[0]) && potentialCost < currentCost) {
                    Operator[] temp = {operators[1], operators[1], operators[2]};
                    output = temp;
                } else if (includedOps.contains(operators[1]) && potentialCost < currentCost) {
                    Operator[] temp = {operators[0], operators[0], operators[2]}; // TODO janky, may cause issue
                    output = temp;
                }
            } else {
                output = operators;
            }
        }

        return output;
    }


    private Operator[] getSmallestPair(ArrayList<Operator[]> allJoinPairings) {
        Operator[] smallestPair = null;

        for (Operator[] operator : allJoinPairings) {

            if (smallestPair != null) {
                int currentPairCost = smallestPair[0].getOutput().getTupleCount() * smallestPair[1].getOutput().getTupleCount();
                int potentialCost = operator[0].getOutput().getTupleCount() * operator[1].getOutput().getTupleCount();

                if (potentialCost < currentPairCost) {
                    smallestPair = operator;
                }
            } else {
                smallestPair = operator;
            }

        }

        return smallestPair;
    }

    private ArrayList<Operator[]> getAllJoinPairings(ArrayList<Operator> smallSelects, ArrayList<Select> joinList) {
        ArrayList<Operator[]> allJoinPairings = new ArrayList<>();

        for (Select select : joinList) {
                Attribute left = select.getPredicate().getLeftAttribute();
                Attribute right = select.getPredicate().getRightAttribute();

                Inspector inspector = new Inspector();
                Operator one = getRelationFromAttribute(left, smallSelects);
                //one.accept(inspector);
                Operator two = getRelationFromAttribute(right, smallSelects);
                //two.accept(inspector);

                Operator[] opArray = {one, two, select};
                allJoinPairings.add(opArray);
        }

        return allJoinPairings;
    }

    private Operator getRelationFromAttribute(Attribute att, ArrayList<Operator> smallSelects) {
        Operator output = null;

        for (Operator operator : smallSelects) {
            if (attributeEquals(att, operator.getOutput().getAttributes())) {
                output = operator;
            }
        }

        return output;
    }

    private boolean attributeEquals(Attribute att, List<Attribute> attributes) {
        for (Attribute attribute : attributes) {
            if (attribute.getName().equals(att.getName())) return true;
        }
        return false;
    }
    
    private void getJoinList (Operator op, ArrayList<Select> joinList) {
        if (op instanceof Select) {
            if (isABigJoin(op)) {
                joinList.add((Select)op);
                getJoinList(goToProduct(op), joinList);
            }
        } else if (op instanceof Project) {
            getJoinList(((Project) op).getInput(), joinList);
        } else if (op instanceof Product) {
            getJoinList(((Product) op).getLeft(), joinList);
        }
    }

    private boolean isABigJoin (Operator op) {
        if (op instanceof Select) {
            return isABigJoin(((Select) op).getInput());
        } else if (op instanceof Product) {
            return true;
        } else return false;
    }

    private Operator goToProduct(Operator op) {
        if (op instanceof Select) {
            return goToProduct(((Select) op).getInput());
        } else if (op instanceof Product) {
            return op;
        } else return null;
    }

    // Return all Selects that have either a scan under them or n number of other selects and a scan
    private void getSmallSelects(Operator op, ArrayList<Operator> selectList) {
        if (op instanceof Select) {
            if (isSelectChain(op)) {
                selectList.add(op);
            } else {
                getSmallSelects(((Select) op).getInput(), selectList);
            }
        } else if (op instanceof Project) {
            getSmallSelects(((Project) op).getInput(), selectList);
        } else if (op instanceof Product) {
            getSmallSelects(((Product) op).getLeft(), selectList);
            getSmallSelects(((Product) op).getRight(), selectList);
        } else if (op instanceof Scan){
            selectList.add(op);
        }
    }

    // Check if the Select is a continuance of other selects
    private boolean isSelectChain(Operator op) {
        if (op instanceof Select) {
            if (((Select) op).getInput() instanceof Scan) {
                return true;
            } else if (((Select) op).getInput() instanceof Select) {
                return isSelectChain(((Select) op).getInput());
            } else {
                return false;
            }
        } else if (op instanceof Scan) return true; // TODO May cause problem
        return false;
    }

    private Operator moveProjectDown(Operator op) {
        Operator output = null;
        ArrayList<Attribute> attributeList = new ArrayList<>();
        Estimator estimator = new Estimator();
        op.accept(estimator);

        output = moveProjectDownHelper(op, attributeList, false);

        return output;
    }

    private Operator moveProjectDownHelper(Operator op, ArrayList<Attribute> attributeArrayList, boolean projectAboveMe) {
        Operator output = null;

        if (projectAboveMe) {
            if (op instanceof Project) {
                output = new Project(moveProjectDownHelper(((Project) op).getInput(), attributeArrayList, true), ((Project) op).getAttributes());
            } else if (op instanceof Select) {
                output = new Select(moveProjectDownHelper(((Select) op).getInput(), attributeArrayList, false), ((Select) op).getPredicate());
            } else if (op instanceof Product) {
                Operator left = moveProjectDownHelper(((Product) op).getLeft(), getOverlappingElementsList(attributeArrayList, ((Product) op).getLeft().getOutput().getAttributes()), false);
                Operator right = moveProjectDownHelper(((Product) op).getRight(), getOverlappingElementsList(attributeArrayList, ((Product) op).getRight().getOutput().getAttributes()), false);

                output = new Product(left, right);
            } else if (op instanceof Join) {
                Operator left = moveProjectDownHelper(((Join) op).getLeft(), getOverlappingElementsList(attributeArrayList, ((Join) op).getLeft().getOutput().getAttributes()), false);
                Operator right = moveProjectDownHelper(((Join) op).getRight(), getOverlappingElementsList(attributeArrayList, ((Join) op).getRight().getOutput().getAttributes()), false);
                Predicate predicate = ((Join) op).getPredicate();

                output = new Join(left, right, predicate);
            } else if (op instanceof Scan) {
                output = op;
            }
        } else {
            if (op instanceof Project) {
                for (Attribute att : ((Project) op).getAttributes()) {
                    attributeArrayList.add(att);
                }
                output = new Project(moveProjectDownHelper(((Project) op).getInput(), attributeArrayList, true), ((Project) op).getAttributes());
            } else if (op instanceof Select) {

                if (((Select) op).getPredicate().equalsValue()) {
                    if (!attributeEquals(((Select) op).getPredicate().getLeftAttribute(), attributeArrayList)) { // TODO may cause issue
                        Select tempSelect = new Select (moveProjectDownHelper(((Select) op).getInput(), attributeArrayList, false), ((Select) op).getPredicate());
                        output = new Project(tempSelect, getOverlappingElementsList(attributeArrayList, op.getOutput().getAttributes()));
                        attributeArrayList.add(((Select) op).getPredicate().getLeftAttribute());
                    } else {
                        output = new Select (moveProjectDownHelper(((Select) op).getInput(), attributeArrayList, false), ((Select) op).getPredicate());
                    }
                } else {
                    if (!attributeEquals(((Select) op).getPredicate().getLeftAttribute(), attributeArrayList) && !attributeEquals(((Select) op).getPredicate().getRightAttribute(), attributeArrayList)) {
                        Select tempSelect = new Select (moveProjectDownHelper(((Select) op).getInput(), attributeArrayList, false), ((Select) op).getPredicate());
                        output = new Project(tempSelect, getOverlappingElementsList(attributeArrayList, op.getOutput().getAttributes()));
                        attributeArrayList.add(((Select) op).getPredicate().getLeftAttribute());
                        attributeArrayList.add(((Select) op).getPredicate().getRightAttribute());
                    } else if (!attributeEquals(((Select) op).getPredicate().getLeftAttribute(), attributeArrayList)) {
                        Select tempSelect = new Select (moveProjectDownHelper(((Select) op).getInput(), attributeArrayList, false), ((Select) op).getPredicate());
                        output = new Project(tempSelect, getOverlappingElementsList(attributeArrayList, op.getOutput().getAttributes()));
                        attributeArrayList.add(((Select) op).getPredicate().getLeftAttribute());
                    } else if (!attributeEquals(((Select) op).getPredicate().getRightAttribute(), attributeArrayList)) {
                        Select tempSelect = new Select (moveProjectDownHelper(((Select) op).getInput(), attributeArrayList, false), ((Select) op).getPredicate());
                        output = new Project(tempSelect, getOverlappingElementsList(attributeArrayList, op.getOutput().getAttributes()));
                        attributeArrayList.add(((Select) op).getPredicate().getRightAttribute());
                    } else {
                        output = new Select (moveProjectDownHelper(((Select) op).getInput(), attributeArrayList, false), ((Select) op).getPredicate());
                    } // TODO may cause massive issues
                }
            } else if (op instanceof Product) {
                Operator left = moveProjectDownHelper(((Product) op).getLeft(), attributeArrayList, false);
                Operator right = moveProjectDownHelper(((Product) op).getRight(), attributeArrayList, false);

                output = new Product(left, right);
            } else if (op instanceof Join) {
                if (!attributeEquals(((Join) op).getPredicate().getLeftAttribute(), attributeArrayList) && !attributeEquals(((Join) op).getPredicate().getRightAttribute(), attributeArrayList)) {
                    Operator left = moveProjectDownHelper(((Join) op).getLeft(), attributeArrayList, false);
                    Operator right = moveProjectDownHelper(((Join) op).getRight(), attributeArrayList, false);

                    Join tempJoin = new Join(left, right, ((Join) op).getPredicate());
                    output = new Project(tempJoin, getOverlappingElementsList(attributeArrayList, op.getOutput().getAttributes()));

                    attributeArrayList.add(((Join) op).getPredicate().getLeftAttribute());
                    attributeArrayList.add(((Join) op).getPredicate().getRightAttribute());
                } else if (!attributeEquals(((Join) op).getPredicate().getLeftAttribute(), attributeArrayList)) {
                    Operator left = moveProjectDownHelper(((Join) op).getLeft(), attributeArrayList, false);
                    Operator right = moveProjectDownHelper(((Join) op).getRight(), attributeArrayList, false);

                    Join tempJoin = new Join(left, right, ((Join) op).getPredicate());
                    output = new Project(tempJoin, getOverlappingElementsList(attributeArrayList, op.getOutput().getAttributes()));

                    attributeArrayList.add(((Join) op).getPredicate().getLeftAttribute());
                } else if (!attributeEquals(((Join) op).getPredicate().getRightAttribute(), attributeArrayList)) {
                    Operator left = moveProjectDownHelper(((Join) op).getLeft(), attributeArrayList, false);
                    Operator right = moveProjectDownHelper(((Join) op).getRight(), attributeArrayList, false);

                    Join tempJoin = new Join(left, right, ((Join) op).getPredicate());
                    output = new Project(tempJoin, getOverlappingElementsList(attributeArrayList, op.getOutput().getAttributes()));

                    attributeArrayList.add(((Join) op).getPredicate().getRightAttribute());
                } else {
                    Operator left = moveProjectDownHelper(((Join) op).getLeft(), attributeArrayList, false);
                    Operator right = moveProjectDownHelper(((Join) op).getRight(), attributeArrayList, false);

                    Join tempJoin = new Join(left, right, ((Join) op).getPredicate());
                    output = tempJoin;
                }


            } else if (op instanceof Scan) {
                return op;
            }
        }


        return output;
    }


    private ArrayList<Attribute> getOverlappingElementsList(ArrayList<Attribute> attList, List<Attribute> attOfRelationList) {
        ArrayList<Attribute> fresh = new ArrayList<>();

        for (Attribute attribute : attList) {
            for (Attribute att2 : attOfRelationList) {
                if (attribute.getName().equals(att2.getName())) {
                    fresh.add(attribute);

                    break;
                }
            }
        }

        return fresh;
    }

    private ArrayList<Attribute> getFreshList(ArrayList<Attribute> attList) {
        ArrayList<Attribute> fresh = new ArrayList<>();

        for (Attribute attribute : attList) {
            fresh.add(attribute);
        }

        return fresh;
    }
    // End of functions for swapping joins
}
