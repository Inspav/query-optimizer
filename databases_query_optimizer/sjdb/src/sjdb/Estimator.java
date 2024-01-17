package sjdb;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class Estimator implements PlanVisitor {


	public Estimator() {
		// empty constructor
	}

	/* 
	 * Create output relation on Scan operator
	 *
	 * Example implementation of visit method for Scan operators.
	 */
	public void visit(Scan op) {
		Relation input = op.getRelation();
		Relation output = new Relation(input.getTupleCount());
		
		Iterator<Attribute> iter = input.getAttributes().iterator();
		while (iter.hasNext()) {
			output.addAttribute(new Attribute(iter.next()));
		}
		
		op.setOutput(output);
	}

	public void visit(Project op) {
		//op.getInput().accept(this);
		Relation input = op.getInput().getOutput();
		Relation output = new Relation(input.getTupleCount());

		for (Attribute attribute : op.getAttributes()) {
			output.addAttribute(input.getAttribute(attribute));
		}

        op.setOutput(output);
	}
	
	public void visit(Select op) {
		//op.getInput().accept(this);

		Relation input = op.getInput().getOutput();
		Relation output;

		if (op.getPredicate().equalsValue()) {
			String value = op.getPredicate().getRightValue();
			Attribute leftAttribute = input.getAttribute(op.getPredicate().getLeftAttribute());

			double result = (double) input.getTupleCount() / (double) leftAttribute.getValueCount();
			output = new Relation((int) Math.ceil(result)) ; // Remember the ceiling part

			for (Attribute attribute : op.getInput().getOutput().getAttributes()) {
				if (attribute.getName().equals(value)) {
					Attribute temp = new Attribute(attribute.getName(), 1);
					output.addAttribute(temp);
				}else output.addAttribute(attribute);
			}
		} else {
			Attribute leftAttribute = input.getAttribute(op.getPredicate().getLeftAttribute());
			Attribute rightAttribute = input.getAttribute(op.getPredicate().getRightAttribute());


			double result = (double) input.getTupleCount() / (double) Math.max(leftAttribute.getValueCount(), rightAttribute.getValueCount());
			output = new Relation((int) Math.ceil(result));

			int distinctAppearances = Math.min(leftAttribute.getValueCount(), rightAttribute.getValueCount());

			for (Attribute attribute : op.getInput().getOutput().getAttributes()) {
				if (attribute.equals(leftAttribute) || attribute.equals(rightAttribute)) {
					Attribute temp = new Attribute(attribute.getName(), distinctAppearances);
					output.addAttribute(temp);
				} else output.addAttribute(attribute);
			}
		}

		op.setOutput(output);
	}


	
	public void visit(Product op) {
		//op.getLeft().accept(this);
		//op.getRight().accept(this);

		Relation inputLeft = op.getLeft().getOutput();
		Relation inputRight = op.getRight().getOutput();

		Relation output = new Relation(inputLeft.getTupleCount() * inputRight.getTupleCount());

		for (Attribute attribute : op.getLeft().getOutput().getAttributes()) {
			output.addAttribute(attribute);
		}
		for (Attribute attribute : op.getRight().getOutput().getAttributes()) {
			output.addAttribute(attribute);
		}

		op.setOutput(output);
	}
	
	public void visit(Join op) {
		//op.getLeft().accept(this);
		//op.getRight().accept(this);

		Relation inputLeft = op.getLeft().getOutput();
		Relation inputRight = op.getRight().getOutput();


		int distinctLeft = inputLeft.getAttribute(op.getPredicate().getLeftAttribute()).getValueCount();
		int distinctRight = inputRight.getAttribute(op.getPredicate().getRightAttribute()).getValueCount();


		double mul = (double) inputLeft.getTupleCount() * inputRight.getTupleCount();
		double div = (double) Math.max(distinctLeft, distinctRight);
		double result = Math.ceil(mul / div);

		int cost = (int) result;
		Relation output = new Relation(cost);

		int minDistinct = Math.min(distinctLeft, distinctRight);

		for (Attribute attribute : inputLeft.getAttributes()) {
			if (attribute.equals(op.getPredicate().getLeftAttribute())) {
				Attribute temp = new Attribute(attribute.getName(), minDistinct);
				output.addAttribute(temp);
			} else output.addAttribute(attribute);
		}
		for (Attribute attribute : inputRight.getAttributes()) {
			if (attribute.equals(op.getPredicate().getRightAttribute())) {
				Attribute temp = new Attribute(attribute.getName(), minDistinct);
				output.addAttribute(temp);
			} else output.addAttribute(attribute);
		}

		op.setOutput(output);
	}


}
