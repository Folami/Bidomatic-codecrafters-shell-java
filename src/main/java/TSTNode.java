// java Program to generate
// autocompleted texts from
// a given prefix using a
// Ternary Search Tree

// The TSTNode class defines the nodes of the tree.
class TSTNode {
	char data;
	boolean isEndOfWord;
	TSTNode left, middle, right;

	public TSTNode(char data)
	{
		this.data = data;
		this.isEndOfWord = false;
		this.left = null;
		this.middle = null;
		this.right = null;
	}
}

