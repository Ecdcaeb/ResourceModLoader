package rml.deserializer.struct.std;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class StructArray extends StructElement implements List<StructElement> {
    protected final List<StructElement> list;

    public StructArray(){
        this.list = new ArrayList<>();
    }


    @Override
    public int size() {
        return this.list.size();
    }

    @Override
    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return this.list.contains(o);
    }

    @Override
    public @NotNull Iterator<StructElement> iterator() {
        return this.list.iterator();
    }

    @Override
    @SuppressWarnings("all")
    public @NotNull Object[] toArray() {
        return this.list.toArray();
    }

    @Override
    @SuppressWarnings("all")
    public @NotNull <T> T[] toArray(@NotNull T[] a) {
        return this.list.toArray(a);
    }

    @Override
    public boolean add(StructElement structElement) {
        return this.list.add(structElement);
    }

    @Override
    public boolean remove(Object o) {
        return this.list.remove(o);
    }

    @Override
    @SuppressWarnings("all")
    public boolean containsAll(@NotNull Collection<?> c) {
        return this.list.containsAll(c);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends StructElement> c) {
        return this.list.addAll(c);
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends StructElement> c) {
        return this.list.addAll(index, c);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        return this.list.removeAll(c);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return this.list.retainAll(c);
    }

    @Override
    public void clear() {
        this.list.clear();
    }

    @Override
    public StructElement get(int index) {
        return this.list.get(index);
    }

    @Override
    public StructElement set(int index, StructElement element) {
        return this.list.set(index, element);
    }

    @Override
    public void add(int index, StructElement element) {
        this.list.add(index, element);
    }

    @Override
    public StructElement remove(int index) {
        return this.list.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return this.list.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return this.list.lastIndexOf(o);
    }

    @Override
    public @NotNull ListIterator<StructElement> listIterator() {
        return this.list.listIterator();
    }

    @Override
    public @NotNull ListIterator<StructElement> listIterator(int index) {
        return this.list.listIterator(index);
    }

    @Override
    public @NotNull List<StructElement> subList(int fromIndex, int toIndex) {
        return this.list.subList(fromIndex, toIndex);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        else if (obj instanceof StructArray array) {
            return array.list.equals(this.list);
        } else return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(list);
    }

    @Override
    public String toString() {
        return Arrays.toString(list.toArray());
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public StructArray getAsArray() {
        return this;
    }
}
