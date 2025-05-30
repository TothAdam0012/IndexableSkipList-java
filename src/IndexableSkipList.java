import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

public class IndexableSkipList<T extends Comparable<? super T>> {
	private final RandomGenerator r;
	private final Node[] lanes;

	// stores the current highest lane index, telling us how many express lanes are present (to know on which level to start a search)
	private int highestLaneIndex;
	private int size;

	// inner classes

	private class Node {
		private Node next;
		private Node prev;
		private int span;
		private final NodeData data;

		private Node(Node next, Node prev, int span, NodeData data) {
			this.next = next;
			this.prev = prev;
			this.span = span;
			this.data = data;
		}
	}

	private class NodeData {
		private final T value;
		private final ArrayList<Node> instances;

		private NodeData(T value, ArrayList<Node> instances) {
			this.value = value;
			this.instances = instances;
		}
	}

	// used for storing the nodes of a search path and their span sum
	// to be able to update their span when a node is inserted
	private class NodeSpan {
		private final Node node;
		private final int spanSum;

		private NodeSpan(Node node, int spanSum) {
			this.node = node;
			this.spanSum = spanSum;
		}
	}

	// constructor

	public IndexableSkipList(@NotNull RandomGenerator randomGenerator) {
		this.r = randomGenerator;
		this.lanes = (Node[]) Array.newInstance(Node.class, 32);
		this.highestLaneIndex = 0;
		this.size = 0;

		// create HEAD node for all lanes
		ArrayList<Node> headInstances = new ArrayList<>(32);
		for(int i = 0; i < 32; i++) {
			Node headNode = new Node(null, null, -1, new NodeData(null, headInstances));
			this.lanes[i] = headNode;
			headInstances.add(headNode); // need the headInstances for the loop in pathToInsert()
		}
	}

	public IndexableSkipList() {
		this(new SplittableRandom());
	}

	// methods

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public boolean insert(@NotNull T element) {
		NodeSpan[] insertPath = pathToInsert(element);
		if(insertPath == null) {
			return false;
		}

		int elementIndex = insertPath[0].node.span == -1 ? 0 : (insertPath[0].spanSum + 1);
		int laneIndex = randomLaneIndex();
		if(highestLaneIndex < laneIndex) {
			highestLaneIndex = laneIndex;
		}
		NodeData nodeData = new NodeData(element, new ArrayList<>(laneIndex + 1));

		for(int i = 0; i <= highestLaneIndex; i++) {
			Node nodeToLeft;
			int spanSumLeft;
			if(i < insertPath.length) {
				nodeToLeft = insertPath[i].node;
				spanSumLeft = insertPath[i].spanSum;
			} else {
				nodeToLeft = lanes[i];
				spanSumLeft = 0;
			}

			int nodeSpan = elementIndex - spanSumLeft;
			if(i <= laneIndex) {
				Node node = new Node(nodeToLeft.next, nodeToLeft, nodeSpan, nodeData);
				nodeToLeft.next = node;
				if(node.next != null) {
					node.next.prev = node;
					node.next.span -= nodeSpan - 1;
				}
				nodeData.instances.add(node);
			} else {
				if(nodeToLeft.next != null) {
					++nodeToLeft.next.span;
				}
			}
		}
		++size;
		return true;
	}

	public boolean remove(T value) {
		Node currentNode = lanes[highestLaneIndex];
		for(int i = highestLaneIndex; i >= 0; i--) {
			currentNode = currentNode.data.instances.get(i);
			while(currentNode.next != null) {
				int comparison = value.compareTo(currentNode.next.data.value);
				if(comparison == 0) {
					removeNode(currentNode.next);
					--size;
					return true;
				}
				if(comparison < 0) break;
				currentNode = currentNode.next;
			}
		}
		return false;
	}

	public T getAtIndex(int index) {
		return getNodeAtIndex(index).data.value;
	}

	public T removeAtIndex(int index) {
		Node node = getNodeAtIndex(index);
		removeNode(node);
		return node.data.value;
	}

	@Override
	public String toString() {
		if(isEmpty()) {
			return "IndexableSkipList []";
		}

		StringBuilder sb = new StringBuilder();
		Node current = lanes[0].next;
		sb.append("IndexableSkipList [");
		while(current != null) {
			sb.append(current.data.value).append(", ");
			current = current.next;
		}
		sb.setLength(sb.length()-2);
		sb.append("]");
		return sb.toString();
	}

	// TODO: FOR DEBUG
	public void print() {
		for(int i = highestLaneIndex; i >= 0; i--) {
			StringBuilder sb = new StringBuilder();
			sb.append("L").append(i).append(": ");
			Node currentNode = lanes[i];
			while(currentNode != null) {
				sb.append(currentNode.data == null ? "null" : currentNode.data.value).append(":").append(currentNode.span).append(", ");
				currentNode = currentNode.next;
			}
			sb.setLength(sb.length() - 2);
			System.out.println(sb);
		}
	}

	// helper methods

	// returns the search path
	// the array for every lane contains the nodes left of the value
	// if the value is already in the list then null is returned (duplicates are not allowed)
	private @Nullable NodeSpan[] pathToInsert(T value) {
		NodeSpan[] leftNodes = (NodeSpan[]) Array.newInstance(NodeSpan.class, highestLaneIndex + 1);
		Node currentNode = lanes[highestLaneIndex];
		int spanSum = 0;
		for(int i = highestLaneIndex; i >= 0; i--) {
			currentNode = currentNode.data.instances.get(i);
			while(currentNode.next != null) {
				int comparison = value.compareTo(currentNode.next.data.value);
				// if the value is already in the list return null to tell the insert method it is a duplicate
				if(comparison == 0) return null;
				if(comparison < 0) break;
				currentNode = currentNode.next;
				spanSum += currentNode.span;
			}
			leftNodes[i] = new NodeSpan(currentNode, spanSum);
		}
		return leftNodes;
	}

	private void removeNode(Node nodeToRemove) {
		for(Node node : nodeToRemove.data.instances) {
			node.prev.next = node.next;
			if(node.next != null) {
				node.next.prev = node.prev;
				node.next.span = node.span - 1;
			}
			node.next = null;
			node.prev = null;
		}
	}

	private Node getNodeAtIndex(int index) {
		if(index < 0 || index >= size) throw new ArrayIndexOutOfBoundsException("no element at index: " + index);
		Node currentNode = lanes[highestLaneIndex];
		int spanSum = 0;
		for(int i = highestLaneIndex; i >= 0; i--) {
			currentNode = currentNode.data.instances.get(i);
			while(currentNode.next != null) {
				int spanSumInc = spanSum + currentNode.next.span;
				if(spanSumInc == index) return currentNode.next;
				if(spanSumInc > index) break;
				currentNode = currentNode.next;
				spanSum += currentNode.span;
			}
		}
		return currentNode;
	}

	// TODO: tweak values
	private int randomLaneIndex() {
		return Math.min(getHighestPossibleLaneIndex(), Integer.numberOfTrailingZeros(r.nextInt()));
	}
	// TODO: tweak values
	private int getHighestPossibleLaneIndex() {
		return Math.max(15, log2(size) + 5);
	}

	private static int log2(int n) {
		return 31 - Integer.numberOfLeadingZeros(n);
	}
}