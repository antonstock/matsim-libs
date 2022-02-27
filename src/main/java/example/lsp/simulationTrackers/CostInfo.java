package example.lsp.simulationTrackers;

import lsp.functions.LSPInfo;

/*package-private*/ class CostInfo extends LSPInfo {

	CostInfo() {
		setFixedCost( null );
		setVariableCost( null );
	}
	void setVariableCost( Double value ){
		this.getAttributes().putAttribute( "variableCost", value );
	}
	void setFixedCost( Double value ){
		this.getAttributes().putAttribute( "fixedCost", value );
	}
	Double getFixedCost() {
		return (Double) this.getAttributes().getAttribute( "fixedCost" );
	}
	Double getVariableCost(){
		return (Double) this.getAttributes().getAttribute( "variableCost" );
	}


	@Override
	public String getName() {
		return "cost_function";
	}

	@Override
	public double getFromTime() {
		return 0;
	}

	@Override
	public double getToTime() {
		return Double.MAX_VALUE;
	}


	@Override
	public void update() {
		// TODO Auto-generated method stub

	}


	@Override
	public void setName(String name) {
		// TODO Auto-generated method stub

	}

}
